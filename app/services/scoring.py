"""
Multi-Pillar Food Scoring Engine
=================================
Deterministic, evidence-based health scoring. The LLM does NOT compute scores —
it only explains results produced here.

Pillars:
  1. Macronutrient Profile   (0–30 pts) + micronutrient bonus (up to +5)
  2. Processing Level        (0–25 pts) — NOVA group
  3. Additive Safety         (0–20 pts) — E-number classification
  4. Official Nutri-Score    (0–15 pts)
  5. Ingredient Integrity    (0–10 pts)

Total: 0–100 (soft ceiling 105 with micronutrient bonus, clamped to 100)

Special overrides:
  - Alcohol: hard cap at 40
  - Serving size: scales Pillar 1 deductions
  - Natural sugar correction: NOVA 1 + high sugar → halve sugar penalty
  - Fortification flag: industrial vitamins get 50% micronutrient credit
  - Category-aware thresholds: each food group has its own nutrient floors
"""

import re
from typing import Optional
from dataclasses import dataclass, field


# ---------------------------------------------------------------------------
# Output dataclass
# ---------------------------------------------------------------------------

@dataclass
class ScoringResult:
    health_score: int
    verdict: str
    is_good_for_health: bool
    health_scale: float
    safe_consumption_frequency: str
    pillar_scores: dict
    deductions: list
    bonuses: list
    data_confidence: float
    category_group: str
    alcohol_capped: bool = False
    glycemic_load: float = 0.0
    allergens: list = field(default_factory=list)
    allergen_warnings: list = field(default_factory=list)


# ---------------------------------------------------------------------------
# Category group detection
# ---------------------------------------------------------------------------

_CATEGORY_KEYWORDS = {
    "oils":          ["oil", "fat", "butter", "margarine", "ghee", "lard"],
    "beverages":     ["beverage", "drink", "juice", "soda", "water", "tea",
                      "coffee", "smoothie", "syrup", "cola", "lemonade", "shake"],
    "dairy":         ["dairy", "milk", "cheese", "yogurt", "yoghurt", "cream",
                      "fromage", "quark", "kefir", "whey"],
    "protein_foods": ["meat", "beef", "chicken", "pork", "poultry", "fish",
                      "seafood", "salmon", "tuna", "egg", "tofu", "tempeh"],
    "whole_foods":   ["fruit", "vegetable", "legume", "bean", "lentil",
                      "nut", "peanut", "cashew", "almond", "pistachio", "walnut", 
                      "seed", "grain", "oat", "quinoa", "rice", "pulse", "plant-based"],
    "condiments":    ["sauce", "condiment", "dressing", "ketchup", "mustard",
                      "vinegar", "spice", "seasoning", "pickle", "relish",
                      "mayo", "mayonnaise", "hot-sauce"],
    "confectionery": ["confection", "chocolate", "candy", "sweet", "dessert",
                      "ice-cream", "ice cream", "gummy", "lolly", "toffee",
                      "praline", "truffle"],
    "grains_bakery": ["cereal", "bread", "pasta", "noodle", "biscuit", "cake",
                      "pastry", "muffin", "cracker", "wafer", "flour",
                      "breakfast-cereal"],
    "snacks":        ["snack", "chip", "crisp", "popcorn", "pretzel",
                      "puff", "stick", "bite"],
}

def detect_category_group(categories: Optional[str]) -> str:
    if not categories:
        return "general"
    cat = categories.lower()
    
    # Specific fix: "plant-based-foods-and-beverages" is a very common top-level category on OFF
    # representing solid whole foods, nuts, etc. Strip it or replace it to prevent false positive beverage match.
    if "plant-based-foods-and-beverages" in cat:
        cat = cat.replace("plant-based-foods-and-beverages", "plant-based-foods")
    if "plant-based foods and beverages" in cat:
        cat = cat.replace("plant-based foods and beverages", "plant-based foods")
        
    for group, keywords in _CATEGORY_KEYWORDS.items():
        if any(k in cat for k in keywords):
            return group
    return "general"


# ---------------------------------------------------------------------------
# Glycemic Load & Allergen Detection
# ---------------------------------------------------------------------------

def calculate_glycemic_load(carbs_per_100g: float, gi_estimate: int) -> float:
    return (carbs_per_100g * gi_estimate) / 100

def get_gi_estimate(food_name: str, group: str) -> int:
    name_lower = (food_name or "").lower()
    if any(x in name_lower for x in ["sugar", "candy", "sweet"]): return 100
    if any(x in name_lower for x in ["bread", "white rice", "pasta"]): return 75
    if any(x in name_lower for x in ["oats", "whole grain", "brown rice"]): return 55
    if group == "whole_foods": return 40
    if group == "dairy": return 30
    return 50 # Default average

COMMON_ALLERGENS = {
    "milk": ["milk", "lactose", "whey", "casein", "buttermilk", "ghee", "paneer"],
    "peanuts": ["peanut", "groundnut", "monkey nuts"],
    "tree_nuts": ["almonds", "cashew", "walnut", "pistachio", "hazelnut"],
    "gluten": ["wheat", "barley", "rye", "gluten", "maida", "suji"],
    "soy": ["soy", "soybean", "edamame"],
    "shellfish": ["shrimp", "crab", "lobster", "mussel"],
    "fish": ["fish", "anchovy", "salmon", "cod"],
    "sesame": ["sesame", "til"],
    "mustard": ["mustard"],
}

def extract_allergens(ingredients_text: str) -> list:
    detected = []
    if not ingredients_text: return detected
    text = ingredients_text.lower()
    for allergen_category, keywords in COMMON_ALLERGENS.items():
        if any(keyword in text for keyword in keywords):
            detected.append(allergen_category)
    return list(set(detected))


# ---------------------------------------------------------------------------
# Category-specific sugar/salt/sat-fat floors (g per 100g)
# Above 1× floor, penalty scales continuously up to maximum cap.
# ---------------------------------------------------------------------------

_THRESHOLDS = {
    # group         sugar  salt  sat_fat  trans_fat  kcal_cap
    "oils":        (2.0,  1.0,  20.0,   0.2,  900),
    "beverages":   (2.5,  0.1,   0.5,   0.0,  150),
    "dairy":       (5.0,  0.5,   4.0,   0.1,  400),
    "protein_foods":(1.0, 0.5,   2.0,   0.1,  350),
    "whole_foods": (8.0,  0.1,   0.5,   0.0,  250),
    "condiments":  (5.0,  2.0,   1.0,   0.0,  400),
    "confectionery":(20.0,0.3,   5.0,   0.3,  600),
    "grains_bakery":(5.0, 0.5,   2.0,   0.1,  450),
    "snacks":      (5.0,  0.75,  2.0,   0.3,  550),
    "general":     (5.0,  0.75,  2.0,   0.5,  500),
}

def _sugar_deduction(value: float, floor: float) -> float:
    if value <= floor:
        return 0.0
    ratio = value / floor
    if ratio >= 9.0:
        return -12.0
    # Smooth continuous linear interpolation between 1.0 (-3) and 9.0 (-12)
    return -3.0 - (ratio - 1.0) * 1.125

def _salt_deduction(value: float, floor: float) -> float:
    if value <= floor:
        return 0.0
    ratio = value / floor
    if ratio >= 3.3:
        return -6.0
    # Smooth continuous linear interpolation between 1.0 (-2) and 3.3 (-6)
    return -2.0 - (ratio - 1.0) * 1.73913

def _sat_fat_deduction(value: float, floor: float, is_protein_food: bool) -> float:
    if value <= floor:
        return 0.0
    ratio = value / floor
    if ratio >= 7.5:
        raw = -8.0
    else:
        # Smooth continuous linear interpolation between 1.0 (-2) and 7.5 (-8)
        raw = -2.0 - (ratio - 1.0) * 0.92307
    if is_protein_food:
        raw = raw * 0.6  # natural meat fat is less harmful
    return raw

def _trans_fat_deduction(value: float, floor: float) -> float:
    if value <= floor:
        return 0.0
    ratio = value / floor
    if ratio >= 4.0:
        return -8.0
    # Smooth continuous linear interpolation between 1.0 (-2) and 4.0 (-8)
    return -2.0 - (ratio - 1.0) * 2.0

def _kcal_deduction(value: float, cap: int) -> float:
    if value <= cap * 0.8:
        return 0.0
    if value >= cap:
        return -4.0
    # Smooth continuous linear interpolation between 0.8 * cap (0) and cap (-4)
    return -20.0 * (value - 0.8 * cap) / cap


# ---------------------------------------------------------------------------
# Heuristic Lists & Detection Helpers
# ---------------------------------------------------------------------------

_ADDED_SUGAR_KEYWORDS = [
    "sugar", "sucrose", "glucose", "fructose", "dextrose", "maltose",
    "syrup", "honey", "jaggery", "molasses", "sweetener", "maltodextrin",
    "agave", "nectar", "treacle", "caramel", "isoglucose", "invert sugar"
]

_ARTIFICIAL_SWEETENERS = [
    "aspartame", "sucralose", "acesulfame", "saccharin", "cyclamate", "neotame",
    "advantame", "sorbitol", "mannitol", "xylitol", "erythritol", "isomalt",
    "lactitol", "maltitol", "stevia", "steviol", "thaumatin", "monk fruit"
]

_NUT_SEED_KEYWORDS = [
    "peanut", "almond", "cashew", "walnut", "pistachio", "pecan", "hazelnut", 
    "macadamia", "brazil nut", "pine nut", "chestnut", "nut seed", "chia seed", 
    "pumpkin seed", "sunflower seed", "flaxseed", "flax seed", "sesame seed", 
    "hemp seed", "raisin", "date", "fig", "apricot", "prune", "sultana", "currant"
]

def _has_added_sugars(ingredients_text: str) -> bool:
    if not ingredients_text:
        return True
    text = ingredients_text.lower()
    return any(keyword in text for keyword in _ADDED_SUGAR_KEYWORDS)

def _has_artificial_sweeteners(ingredients_text: str, additives_tags: list) -> bool:
    if ingredients_text:
        text = ingredients_text.lower()
        if any(keyword in text for keyword in _ARTIFICIAL_SWEETENERS):
            return True
            
    sweetener_codes = {
        "e950", "e951", "e952", "e953", "e954", "e955", "e957", "e959",
        "e960", "e961", "e962", "e965", "e966", "e967", "e968"
    }
    for tag in additives_tags:
        code = _extract_enum(tag)
        if code in sweetener_codes:
            return True
            
    return False

def _is_nut_seed_or_dried_fruit(name: str, category: str, ingredients_text: str) -> bool:
    name_l = name.lower() if name else ""
    cat_l = category.lower() if category else ""
    ing_l = ingredients_text.lower() if ingredients_text else ""
    
    for keyword in _NUT_SEED_KEYWORDS:
        if keyword in name_l or keyword in cat_l:
            return True
            
    if ing_l and len(ing_l) > 0 and len(ing_l) < 30:
        for keyword in _NUT_SEED_KEYWORDS:
            if keyword in ing_l:
                return True
                
    return False


# ---------------------------------------------------------------------------
# Pillar 1 — Macronutrient Profile
# ---------------------------------------------------------------------------

def _score_macronutrients(
    nutrients: dict, 
    group: str, 
    nova_group: Optional[int],
    nutrient_levels: dict,
    ingredients_text: str,
    additives_tags: list,
    is_nut_or_seed: bool,
    ingredients_count: int
) -> tuple[float, list, list]:
    """Returns (score, deductions, bonuses). Score range: 0–35."""
    floors = _THRESHOLDS.get(group, _THRESHOLDS["general"])
    sugar_floor, salt_floor, sat_fat_floor, trans_floor, kcal_cap = floors
    is_protein = (group == "protein_foods")

    # Detect data availability
    key_count = sum(1 for k in ["sugars_100g", "fat_100g", "proteins_100g",
                                 "salt_100g", "fiber_100g", "energy-kcal_100g"]
                    if k in nutrients)

    deductions, bonuses = [], []

    if key_count >= 2:
        base = 30.0
        sugar  = nutrients.get("sugars_100g", 0) or 0
        salt   = nutrients.get("salt_100g") or (nutrients.get("sodium_100g", 0) or 0) * 2.5
        sat_fat= nutrients.get("saturated-fat_100g", 0) or 0
        trans  = nutrients.get("trans-fat_100g", 0) or 0
        kcal   = nutrients.get("energy-kcal_100g") or (nutrients.get("energy_100g", 0) or 0) / 4.184
        fiber  = nutrients.get("fiber_100g", 0) or 0
        protein= nutrients.get("proteins_100g", 0) or 0

        # Heuristic 1: Natural sugar penalty reduction
        has_added_sugar = _has_added_sugars(ingredients_text)
        
        s_ded = _sugar_deduction(sugar, sugar_floor)
        if s_ded < 0:
            if not has_added_sugar:
                s_ded = s_ded * 0.2
                bonuses.append({
                    "reason": f"Natural sugar ({sugar:.1f}g/100g) — unsweetened product — penalty discounted by 80%", 
                    "points": round(abs(s_ded * 4.0), 1)
                })
            elif nova_group == 1:
                s_ded = s_ded * 0.5
                bonuses.append({
                    "reason": f"Natural sugar ({sugar:.1f}g/100g) in unprocessed whole food — penalty halved", 
                    "points": round(abs(s_ded), 1)
                })
            
            if s_ded < -0.1:
                deductions.append({
                    "reason": f"Sugar: {sugar:.1f}g/100g (group safe floor: {sugar_floor}g)", 
                    "points": round(s_ded, 1)
                })

        sl_ded = _salt_deduction(salt, salt_floor)
        if sl_ded < -0.1:
            deductions.append({
                "reason": f"Salt: {salt:.2f}g/100g (group safe floor: {salt_floor}g)", 
                "points": round(sl_ded, 1)
            })

        # Heuristic 2: Saturated fat penalty reduction for nuts/seeds or single-ingredients
        is_single_ingredient = (ingredients_count == 1)
        sf_ded = _sat_fat_deduction(sat_fat, sat_fat_floor, is_protein)
        if sf_ded < 0:
            if is_nut_or_seed:
                sf_ded = sf_ded * 0.2
                bonuses.append({
                    "reason": "Cardioprotective natural fats in nuts/seeds — saturated fat penalty discounted by 80%",
                    "points": round(abs(sf_ded * 4.0), 1)
                })
            elif is_single_ingredient:
                sf_ded = sf_ded * 0.5
                bonuses.append({
                    "reason": "Single-ingredient natural whole fat — saturated fat penalty halved",
                    "points": round(abs(sf_ded), 1)
                })
                
            if sf_ded < -0.1:
                deductions.append({
                    "reason": f"Saturated fat: {sat_fat:.1f}g/100g (group safe floor: {sat_fat_floor}g)", 
                    "points": round(sf_ded, 1)
                })

        tf_ded = _trans_fat_deduction(trans, trans_floor)
        if tf_ded < -0.1:
            deductions.append({
                "reason": f"Trans fat: {trans:.2f}g/100g", 
                "points": round(tf_ded, 1)
            })

        kd_ded = _kcal_deduction(kcal, kcal_cap)
        if kd_ded < 0:
            if is_nut_or_seed:
                kd_ded = 0.0
                bonuses.append({
                    "reason": "Nutrient-dense whole nuts/seeds — high caloric density penalty waived",
                    "points": 0.0
                })
            elif is_single_ingredient:
                kd_ded = kd_ded * 0.5
                bonuses.append({
                    "reason": "Single-ingredient natural food — high caloric density penalty halved",
                    "points": round(abs(kd_ded), 1)
                })
                
            if kd_ded < -0.1:
                deductions.append({
                    "reason": f"High caloric density: {kcal:.0f} kcal/100g", 
                    "points": round(kd_ded, 1)
                })

        # Oils — fat content not penalised
        if group == "oils":
            sf_ded = max(sf_ded, -3.0)  # soften sat fat cap for oils

        fiber_bonus = 6 if fiber >= 8 else (4 if fiber >= 5 else (2 if fiber >= 3 else 0))
        if fiber_bonus:
            bonuses.append({"reason": f"Good fiber: {fiber:.1f}g/100g", "points": fiber_bonus})

        protein_bonus = 4 if protein >= 25 else (3 if protein >= 15 else (2 if protein >= 8 else 0))
        if protein_bonus:
            bonuses.append({"reason": f"Good protein: {protein:.1f}g/100g", "points": protein_bonus})

        # Heuristic 3: Diet Soda / Artificial Sweetener "Chemical Masquerade" Penalty
        sweetener_penalty = 0.0
        if _has_artificial_sweeteners(ingredients_text, additives_tags):
            sweetener_penalty = -10.0
            deductions.append({
                "reason": "Artificially sweetened — chemical masquerade penalty applied", 
                "points": -10.0
            })

        total_ded = s_ded + sl_ded + sf_ded + tf_ded + kd_ded + sweetener_penalty
        score = base + total_ded + fiber_bonus + protein_bonus

    else:
        # Fallback: use nutrient_levels dict (high/moderate/low)
        base = 15.0
        score = base
        level_map = {"high": -4.0, "moderate": -2.0, "low": 0.0}
        for nutrient_key in ["sugars", "fat", "salt", "saturated-fat"]:
            level = nutrient_levels.get(nutrient_key, "")
            ded = level_map.get(level, 0.0)
            if ded:
                deductions.append({
                    "reason": f"{nutrient_key} level: {level} (from nutrient_levels, no exact data)", 
                    "points": ded
                })
                score += ded

    return max(0.0, min(35.0, score)), deductions, bonuses


# ---------------------------------------------------------------------------
# Pillar 1 Addition — Micronutrient Bonus (up to +5)
# ---------------------------------------------------------------------------

_MICRONUTRIENTS = [
    ("vitamin-c_100g",   12.0,   "Vitamin C"),
    ("vitamin-d_100g",    1.5,   "Vitamin D"),
    ("calcium_100g",    120.0,   "Calcium"),
    ("iron_100g",         2.1,   "Iron"),
    ("potassium_100g",  350.0,   "Potassium"),
    ("magnesium_100g",   56.0,   "Magnesium"),
    ("zinc_100g",         1.5,   "Zinc"),
    ("vitamin-b12_100g",  0.375, "Vitamin B12"),
]

def _score_micronutrients(nutrients: dict, ingredients_text: str,
                           nova_group: Optional[int]) -> tuple[int, list]:
    """Returns (bonus_pts, bonuses). Max +5."""
    is_fortified = any(phrase in ingredients_text.lower()
                       for phrase in ["fortified with", "enriched with", "added vitamins", "added minerals"])
    fortified_and_ultra = is_fortified and nova_group == 4

    bonus, bonuses = 0, []
    for key, threshold, label in _MICRONUTRIENTS:
        val = nutrients.get(key, 0) or 0
        if val >= threshold:
            pts = 1
            if fortified_and_ultra:
                pts = 0  # industrial fortification gets no credit in ultra-processed
            else:
                bonus += pts
                bonuses.append({"reason": f"{label}: {val:.2f} (≥ 15% RDA per 100g)", "points": pts})

    if fortified_and_ultra and bonus == 0 and any(
        (nutrients.get(k, 0) or 0) >= t for k, t, _ in _MICRONUTRIENTS
    ):
        bonuses.append({"reason": "Micronutrients industrially added to ultra-processed food — no credit awarded", "points": 0})

    return min(5, bonus), bonuses


# ---------------------------------------------------------------------------
# Pillar 2 — Processing Level (NOVA)
# ---------------------------------------------------------------------------

_NOVA_SCORES = {1: 25, 2: 20, 3: 12, 4: 0}

def _score_processing(nova_group: Optional[int], additives_count: int) -> tuple[int, list, list]:
    deductions, bonuses = [], []
    if nova_group is not None:
        score = _NOVA_SCORES.get(nova_group, 10)
        label = {1: "Unprocessed/minimally processed",
                 2: "Processed culinary ingredient",
                 3: "Processed food",
                 4: "Ultra-processed food"}
        if nova_group == 4:
            deductions.append({"reason": f"Ultra-processed food (NOVA 4) — maximum processing penalty", "points": -25})
        elif nova_group == 3:
            deductions.append({"reason": f"Processed food (NOVA 3)", "points": -13})
        else:
            bonuses.append({"reason": f"{label.get(nova_group, 'NOVA ' + str(nova_group))} (NOVA {nova_group})", "points": score})
        return score, deductions, bonuses

    # Estimate from additive count
    if additives_count == 0:
        score = 18
        bonuses.append({"reason": "No additives detected — likely minimally processed (estimated)", "points": 18})
    elif additives_count <= 2:
        score = 13
    elif additives_count <= 5:
        score = 8
    elif additives_count <= 10:
        score = 4
    else:
        score = 0
        deductions.append({"reason": f"High additive count ({additives_count}) suggests ultra-processing (estimated)", "points": 0})

    return score, deductions, bonuses


# ---------------------------------------------------------------------------
# Pillar 3 — Additive Safety
# ---------------------------------------------------------------------------

_RED_ADDITIVES = {
    "e102", "e110", "e122", "e124", "e129",  # artificial azo colors (ADHD linked)
    "e211",                                    # sodium benzoate (carcinogenic potential)
    "e249", "e250", "e251", "e252",            # nitrites/nitrates
    "e621",                                    # MSG
    "e951",                                    # aspartame
    "e952",                                    # cyclamate (banned in USA)
    "e954",                                    # saccharin
}

_ORANGE_ADDITIVES = {
    "e150a", "e150b", "e150c", "e150d",        # caramel colorings (4-MEI)
    "e320", "e321",                             # BHA, BHT (possible carcinogens)
    "e407",                                     # carrageenan (gut inflammation)
    "e471", "e472a", "e472b", "e472c",          # mono/diglycerides
    "e955",                                     # sucralose
    "e635",                                     # disodium ribonucleotide (gout risk)
}

_GREEN_ADDITIVES = {
    "e300",                                     # Vitamin C
    "e306", "e307", "e308", "e309",             # Vitamin E tocopherols
    "e101",                                     # Riboflavin (B2)
    "e160a",                                    # Beta-carotene
    "e270",                                     # Lactic acid
    "e296",                                     # Malic acid
    "e330",                                     # Citric acid
}

def _extract_enum(tag: str) -> str:
    """'en:e621' → 'e621', 'e-621' → 'e621'"""
    match = re.search(r'e-?(\d+[a-z]*)', tag.lower())
    return f"e{match.group(1)}" if match else tag.lower()

def _score_additives(additives_tags: list, group: str, serving_grams: Optional[float]) -> tuple[int, list, list]:
    base = 20
    deductions, bonuses = [], []
    is_condiment = (group == "condiments")
    small_serving = (serving_grams is not None and serving_grams < 15)

    for tag in additives_tags:
        code = _extract_enum(tag)
        if code in _RED_ADDITIVES:
            pts = -5
            if is_condiment and small_serving:
                pts = -3  # halved for small serving
            deductions.append({"reason": f"{code.upper()} — high-risk additive (artificial color/preservative/sweetener)", "points": pts})
            base += pts
        elif code in _ORANGE_ADDITIVES:
            pts = -3
            if is_condiment and small_serving:
                pts = -2
            deductions.append({"reason": f"{code.upper()} — moderate-risk additive", "points": pts})
            base += pts
        elif code in _GREEN_ADDITIVES:
            bonuses.append({"reason": f"{code.upper()} — safe/beneficial additive (natural)", "points": 0})
        else:
            pts = -1
            if is_condiment and small_serving:
                pts = 0
            if pts:
                deductions.append({"reason": f"{code.upper()} — low-risk additive", "points": pts})
                base += pts

    return max(0, base), deductions, bonuses


# ---------------------------------------------------------------------------
# Pillar 4 — Official Nutri-Score
# ---------------------------------------------------------------------------

_NUTRI_SCORE_MAP = {"a": 15, "b": 12, "c": 9, "d": 5, "e": 2}

def _score_nutriscore(nutri_score: Optional[str]) -> tuple[int, list, list]:
    if not nutri_score or nutri_score.lower() in ["unknown", "not-applicable"]:
        return 7, [], [{"reason": "Nutri-Score not available — neutral score applied", "points": 7}]
    grade = nutri_score.lower().strip()
    pts = _NUTRI_SCORE_MAP.get(grade, 7)
    label = f"Nutri-Score {grade.upper()}"
    if pts >= 12:
        return pts, [], [{"reason": label, "points": pts}]
    return pts, [{"reason": label, "points": pts - 15}], []


# ---------------------------------------------------------------------------
# Pillar 5 — Ingredient Integrity
# ---------------------------------------------------------------------------

_RED_FLAG_INGREDIENTS = [
    ("high fructose corn syrup",   -3),
    ("fructose-glucose syrup",     -3),
    ("glucose-fructose syrup",     -3),
    ("partially hydrogenated",     -3),
    ("trans fat",                  -3),
    ("hydrogenated",               -2),
    ("corn syrup",                 -2),
    ("artificial colour",          -1),
    ("artificial color",           -1),
    ("artificial flavor",          -1),
    ("artificial flavour",         -1),
    ("modified starch",            -1),
]

_SUGAR_FIRST_TERMS = {"sugar", "sucrose", "glucose", "fructose", "dextrose",
                       "corn syrup", "cane sugar", "invert sugar"}

def _score_ingredient_integrity(ingredients: list, ingredients_text: str, additives_tags: list = None) -> tuple[float, list, list]:
    base = 10.0
    deductions, bonuses = [], []
    text_lower = ingredients_text.lower()
    additives_tags = additives_tags or []

    # Count penalty
    count = len(ingredients)
    if count > 30:
        d = -4.0
    elif count > 20:
        d = -3.0
    elif count > 10:
        d = -2.0
    elif count > 5:
        d = -1.0
    else:
        d = 0.0
    if d:
        deductions.append({"reason": f"High ingredient count ({count}) — indicator of complexity/processing", "points": d})
        base += d

    # Red flag scan (max -4 total from flags)
    flag_total = 0.0
    for phrase, pts in _RED_FLAG_INGREDIENTS:
        if phrase in text_lower and flag_total > -4.0:
            deductions.append({"reason": f"Contains '{phrase}'", "points": pts})
            base += pts
            flag_total += pts

    # Refined / Inflammatory Oils Scan (max -4.0)
    oil_ded = 0.0
    detected_oils = []
    if any(p in text_lower for p in ["palm oil", "palm olein", "palm kernel", "hydrogenated palm"]):
        oil_ded = -4.0
        detected_oils.append("palm oil/olein")
    elif any(p in text_lower for p in ["soybean oil", "soy oil", "vegetable oil", "canola oil", "rapeseed oil", "sunflower oil", "corn oil", "cottonseed oil"]):
        oil_ded = -2.0
        detected_oils.append("refined vegetable oil")
    if oil_ded < 0.0:
        deductions.append({
            "reason": f"Contains refined inflammatory oil ({', '.join(detected_oils)})", 
            "points": oil_ded
        })
        base += oil_ded

    # Glycemic Fillers Scan (max -3.0)
    filler_ded = 0.0
    detected_fillers = []
    if "maltodextrin" in text_lower:
        filler_ded -= 2.0
        detected_fillers.append("maltodextrin")
    if any(p in text_lower for p in ["modified starch", "modified corn starch", "modified tapioca starch", "dextrin"]):
        filler_ded -= 1.5
        detected_fillers.append("modified starch/dextrin")
    
    filler_ded = max(-3.0, filler_ded)
    if filler_ded < 0.0:
        deductions.append({
            "reason": f"Contains high-glycemic filler ({', '.join(detected_fillers)})", 
            "points": filler_ded
        })
        base += filler_ded

    # Artificial Sweetener scanner in Pillar 5 (-3.0)
    if _has_artificial_sweeteners(ingredients_text, additives_tags):
        deductions.append({
            "reason": "Contains artificial/synthetic sweetener (integrity penalty)", 
            "points": -3.0
        })
        base += -3.0

    # Sugar-first penalty
    if ingredients:
        first = ingredients[0].lower()
        if any(term in first for term in _SUGAR_FIRST_TERMS):
            deductions.append({"reason": "Sugar/sweetener is the primary ingredient (listed first by weight)", "points": -2.0})
            base -= 2.0

    return max(0.0, min(10.0, base)), deductions, bonuses


# ---------------------------------------------------------------------------
# Serving size parser
# ---------------------------------------------------------------------------

def _parse_serving_grams(serving_size: Optional[str]) -> Optional[float]:
    if not serving_size:
        return None
    match = re.search(r'(\d+(?:\.\d+)?)\s*(?:g|ml|gram)', serving_size.lower())
    return float(match.group(1)) if match else None


def _serving_multiplier(serving_grams: Optional[float]) -> float:
    if serving_grams is None:
        return 1.0
    if serving_grams < 15:   return 0.6
    if serving_grams < 30:   return 0.85
    if serving_grams <= 100: return 1.0
    return 1.15


# ---------------------------------------------------------------------------
# Data confidence
# ---------------------------------------------------------------------------

def _compute_confidence(nutrients: dict, nutrient_levels: dict,
                         nova_group: Optional[int], additives_tags: list,
                         nutri_score: Optional[str], ingredients_text: str) -> float:
    score = 0.0
    nk = len(nutrients)
    if nk >= 5:
        score += 0.30
    elif nk >= 1:
        score += 0.15
    if nutrient_levels and nk < 3:
        score += 0.10
    if nova_group is not None:
        score += 0.25
    if additives_tags is not None:         # even empty list = we know there are none
        score += 0.20
    if nutri_score and nutri_score.lower() not in ["unknown", "not-applicable", ""]:
        score += 0.15
    if ingredients_text:
        score += 0.10
    return min(1.0, round(score, 2))


# ---------------------------------------------------------------------------
# Derived fields
# ---------------------------------------------------------------------------

def _frequency(score: int) -> str:
    if score >= 80: return "Daily"
    if score >= 65: return "3–4 times per week"
    if score >= 50: return "Weekly"
    if score >= 35: return "Rarely (once or twice a month)"
    if score >= 20: return "Avoid — consume only on special occasions"
    return "Never recommended"


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

def compute_health_score(
    nutrients: dict,
    nova_group: Optional[int],
    nutri_score: Optional[str],
    additives_tags: list,
    nutrient_levels: dict,
    ingredients: list,
    ingredients_text: str,
    categories: Optional[str],
    serving_size: Optional[str],
    product_name: Optional[str] = None,
) -> ScoringResult:
    """
    Run the full 5-pillar scoring engine and return a ScoringResult.
    All inputs come directly from OpenFoodFacts data.
    """
    nutrients       = nutrients or {}
    additives_tags  = additives_tags or []
    nutrient_levels = nutrient_levels or {}
    ingredients     = ingredients or []
    ingredients_text= ingredients_text or ""

    group = detect_category_group(categories)
    serving_grams = _parse_serving_grams(serving_size)

    all_deductions, all_bonuses = [], []

    # ── Alcohol override (hard cap) ─────────────────────────────────────────
    alcohol = nutrients.get("alcohol_100g", 0) or 0
    alcohol_capped = alcohol > 0.5

    # Detect nut/seed/dried fruit
    is_nut_or_seed = _is_nut_seed_or_dried_fruit(product_name, categories, ingredients_text)

    # ── Pillar 1: Macronutrients ────────────────────────────────────────────
    p1_base, p1_ded, p1_bon = _score_macronutrients(
        nutrients=nutrients,
        group=group,
        nova_group=nova_group,
        nutrient_levels=nutrient_levels,
        ingredients_text=ingredients_text,
        additives_tags=additives_tags,
        is_nut_or_seed=is_nut_or_seed,
        ingredients_count=len(ingredients),
    )

    # Apply serving size multiplier to Pillar 1 deductions only
    srv_mult = _serving_multiplier(serving_grams)
    if srv_mult != 1.0:
        adjusted_p1_ded = []
        for d in p1_ded:
            new_pts = round(d["points"] * srv_mult, 1)
            adjusted_p1_ded.append({
                **d,
                "points": new_pts,
                "note": f"adjusted for serving size ({serving_grams:.0f}g)" if srv_mult < 1 else None
            })
        p1_ded = adjusted_p1_ded
        p1_base_adjusted = 30.0 + sum(d["points"] for d in p1_ded) + sum(b["points"] for b in p1_bon)
        p1_base = max(0.0, min(35.0, p1_base_adjusted))

    all_deductions.extend(p1_ded)
    all_bonuses.extend(p1_bon)

    # ── Pillar 1 addition: Micronutrients ───────────────────────────────────
    micro_bonus, micro_bon = _score_micronutrients(nutrients, ingredients_text, nova_group)
    all_bonuses.extend(micro_bon)

    # ── Pillar 2: Processing ────────────────────────────────────────────────
    p2, p2_ded, p2_bon = _score_processing(nova_group, len(additives_tags))
    all_deductions.extend(p2_ded)
    all_bonuses.extend(p2_bon)

    # ── Pillar 3: Additives ─────────────────────────────────────────────────
    p3, p3_ded, p3_bon = _score_additives(additives_tags, group, serving_grams)
    all_deductions.extend(p3_ded)
    all_bonuses.extend(p3_bon)

    # ── Pillar 4: Nutri-Score ───────────────────────────────────────────────
    p4, p4_ded, p4_bon = _score_nutriscore(nutri_score)
    all_deductions.extend(p4_ded)
    all_bonuses.extend(p4_bon)

    # ── Pillar 5: Ingredient integrity ─────────────────────────────────────
    p5, p5_ded, p5_bon = _score_ingredient_integrity(
        ingredients=ingredients,
        ingredients_text=ingredients_text,
        additives_tags=additives_tags,
    )
    all_deductions.extend(p5_ded)
    all_bonuses.extend(p5_bon)

    # ── Hackathon Additions: Glycemic Load & Allergens ─────────────────────
    carbs = nutrients.get("carbohydrates_100g", 0) or 0
    gi = get_gi_estimate(product_name, group)
    gl = calculate_glycemic_load(carbs, gi)
    
    # Penalize if GL is very high (above 20 is considered high)
    if gl > 20.0:
        gl_penalty = min(10.0, (gl - 20.0) / 2.0)
        all_deductions.append({
            "reason": f"High Glycemic Load ({gl:.1f}) — rapid blood sugar spike",
            "points": -gl_penalty
        })
        p1_base -= gl_penalty # apply penalty to macronutrient pillar for now

    detected_allergens = extract_allergens(ingredients_text)
    allergen_warnings = [f"Contains {a.replace('_', ' ')}" for a in detected_allergens]

    # ── Total ───────────────────────────────────────────────────────────────
    raw = p1_base + micro_bonus + p2 + p3 + p4 + p5

    if alcohol_capped:
        raw = min(raw, 40.0)
        all_deductions.insert(0, {
            "reason": f"Contains alcohol ({alcohol:.1f}g/100g) — score hard-capped at 40",
            "points": "cap"
        })

    # Hard cap for artificial sweeteners (Diet Soda Loophole Fix)
    has_sweeteners = _has_artificial_sweeteners(ingredients_text, additives_tags)
    if has_sweeteners:
        raw = min(raw, 65.0)
        all_deductions.insert(0, {
            "reason": "Contains artificial/synthetic sweeteners — score hard-capped at 65 to prevent chemical masquerading",
            "points": "cap"
        })

    # Missing ingredients penalty
    if not ingredients_text:
        raw = min(raw, 75.0)
        all_deductions.insert(0, {
            "reason": "Missing ingredient data — score penalized and capped at 75",
            "points": -10.0
        })
        raw = max(0.0, raw - 10.0)

    health_score = int(max(0.0, min(100.0, raw)))

    confidence = _compute_confidence(
        nutrients, nutrient_levels, nova_group,
        additives_tags, nutri_score, ingredients_text
    )

    return ScoringResult(
        health_score=health_score,
        verdict="SMASH" if health_score >= 60 else "PASS",
        is_good_for_health=health_score >= 60,
        health_scale=round(health_score / 10.0, 1),
        safe_consumption_frequency=_frequency(health_score),
        pillar_scores={
            "macronutrient":        p1_base,
            "micronutrient_bonus":  micro_bonus,
            "processing":           p2,
            "additive_safety":      p3,
            "nutri_score_pts":      p4,
            "ingredient_integrity": p5,
        },
        deductions=[d for d in all_deductions if d.get("points") != 0],
        bonuses=all_bonuses,
        data_confidence=confidence,
        category_group=group,
        alcohol_capped=alcohol_capped,
        glycemic_load=gl,
        allergens=detected_allergens,
        allergen_warnings=allergen_warnings,
    )
