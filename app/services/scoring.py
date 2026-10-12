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
    # Plain-language reasons, biggest effect first (what the app shows as "why this score")
    concerns: list = field(default_factory=list)
    positives: list = field(default_factory=list)


# ---------------------------------------------------------------------------
# Category group detection
# ---------------------------------------------------------------------------

_CATEGORY_KEYWORDS = {
    "oils":          ["oil", "fat", "butter", "margarine", "ghee", "lard", "vanaspati"],
    "beverages":     ["beverage", "drink", "juice", "soda", "water", "tea",
                      "coffee", "smoothie", "syrup", "cola", "lemonade", "shake",
                      "nectar", "squash", "sharbat"],
    "dairy":         ["dairy", "milk", "cheese", "yogurt", "yoghurt", "cream",
                      "fromage", "quark", "kefir", "whey", "paneer", "curd", "dahi",
                      "lassi", "buttermilk", "chaas"],
    "protein_foods": ["meat", "beef", "chicken", "pork", "poultry", "fish",
                      "seafood", "salmon", "tuna", "egg", "tofu", "tempeh", "mutton",
                      "prawn", "sausage", "ham"],
    "whole_foods":   ["fruit", "vegetable", "legume", "bean", "lentil", "pulse", "dal",
                      "nut", "peanut", "cashew", "almond", "pistachio", "walnut",
                      "seed", "grain", "oat", "quinoa", "rice", "millet", "potato",
                      "dried fruit"],
    "condiments":    ["sauce", "condiment", "dressing", "ketchup", "mustard",
                      "vinegar", "spice", "seasoning", "pickle", "relish",
                      "mayo", "mayonnaise", "hot sauce", "masala", "chutney", "achar"],
    "confectionery": ["confection", "confectionery", "chocolate", "candy", "candies",
                      "sweet", "dessert", "ice cream", "gummy", "gummies", "lolly",
                      "toffee", "praline", "truffle", "mithai", "indian sweet",
                      "laddu", "halwa", "barfi", "jam"],
    "grains_bakery": ["cereal", "bread", "pasta", "noodle", "biscuit", "cookie", "cake",
                      "pastry", "pastries", "muffin", "cracker", "wafer", "flour",
                      "breakfast cereal", "atta", "rusk", "khakhra", "vermicelli"],
    "snacks":        ["snack", "chip", "crisp", "popcorn", "pretzel", "puff",
                      "namkeen", "bhujia", "chivda", "chiwda", "mixture", "papad",
                      "nachos", "makhana", "farsan"],
}

# Compiled once: whole words only (with optional plural), so "nut" no longer matches
# "coconut", "oat" no longer matches "coated" and "egg" no longer matches "veggie".
_KEYWORD_PATTERNS = [
    (group, kw, re.compile(rf"\b{re.escape(kw)}(?:s|es)?\b"))
    for group, kws in _CATEGORY_KEYWORDS.items()
    for kw in kws
]

# OFF's own food groups (computed from its category taxonomy) are the most reliable signal.
_FOOD_GROUP_MAP = {
    # level 2 (specific)
    "en:fats": "oils",
    "en:sweetened-beverages": "beverages",
    "en:unsweetened-beverages": "beverages",
    "en:artificially-sweetened-beverages": "beverages",
    "en:fruit-juices": "beverages",
    "en:fruit-nectars": "beverages",
    "en:teas-and-herbal-teas-and-coffees": "beverages",
    "en:waters-and-flavored-waters": "beverages",
    "en:plant-based-milk-substitutes": "beverages",
    "en:alcoholic-beverages": "beverages",
    "en:milk-and-yogurt": "dairy",
    "en:cheese": "dairy",
    "en:dairy-desserts": "dairy",
    "en:meat": "protein_foods",
    "en:processed-meat": "protein_foods",
    "en:fish-and-seafood": "protein_foods",
    "en:eggs": "protein_foods",
    "en:offals": "protein_foods",
    "en:fruits": "whole_foods",
    "en:vegetables": "whole_foods",
    "en:legumes": "whole_foods",
    "en:nuts": "whole_foods",
    "en:dried-fruits": "whole_foods",
    "en:dressings-and-sauces": "condiments",
    "en:sweets": "confectionery",
    "en:chocolate-products": "confectionery",
    "en:ice-cream": "confectionery",
    "en:biscuits-and-cakes": "grains_bakery",
    "en:pastries": "grains_bakery",
    "en:bread": "grains_bakery",
    "en:breakfast-cereals": "grains_bakery",
    "en:cereals": "grains_bakery",
    "en:appetizers": "snacks",
    "en:salty-and-fatty-products": "snacks",
    # level 1 (broad) — only used when no level-2 group is present
    "en:salty-snacks": "snacks",
    "en:sugary-snacks": "confectionery",
    "en:beverages": "beverages",
    "en:milk-and-dairy-products": "dairy",
    "en:fish-meat-eggs": "protein_foods",
    "en:fruits-and-vegetables": "whole_foods",
    "en:cereals-and-potatoes": "grains_bakery",
}

# OFF food groups that also hold processed foods: "potatoes" includes crisps and fries, which scored
# Lay's-type chips as a whole vegetable. The category tags decide; the group is only the fallback.
_AMBIGUOUS_FOOD_GROUPS = {"en:potatoes": "whole_foods"}

# Top-level OFF categories that say nothing about what the food is.
_UNINFORMATIVE_TAGS = {
    "plant based foods and beverages", "plant based foods", "foods", "groceries",
    "beverages and beverages preparations", "unknown", "dried products",
}

CATEGORY_LABELS = {
    "oils": "cooking fats and oils",
    "beverages": "drinks",
    "dairy": "dairy products",
    "protein_foods": "meat, fish and eggs",
    "whole_foods": "whole foods (fruit, vegetables, pulses, nuts, grains)",
    "condiments": "sauces and condiments",
    "confectionery": "sweets and chocolate",
    "grains_bakery": "biscuits, breads and cereals",
    "snacks": "savoury snacks",
    "general": "packaged foods",
}


def _split_tags(categories) -> list:
    """Accepts OFF categories as a list or a comma-separated string ('en:snacks,en:salty-snacks')."""
    if not categories:
        return []
    raw = categories if isinstance(categories, list) else str(categories).split(",")
    tags = []
    for t in raw:
        t = t.strip().lower()
        if ":" in t:
            t = t.split(":", 1)[1]
        t = t.replace("-", " ").replace("_", " ").strip()
        if t:
            tags.append(t)
    return tags


def _group_for_tag(tag: str) -> Optional[str]:
    """Picks the group of the head noun: English puts it last ('chocolate biscuits' → biscuits)."""
    best = None  # (end position, match length, group)
    for group, kw, pattern in _KEYWORD_PATTERNS:
        for m in pattern.finditer(tag):
            key = (m.end(), m.end() - m.start())
            if best is None or key > best[0]:
                best = (key, group)
    return best[1] if best else None


def detect_category_group(categories, food_groups: Optional[list] = None) -> str:
    """
    OFF lists categories from broad to specific ('snacks, sweet snacks, biscuits'), so the most
    specific tag is checked first. Previously only the broadest tag was stored and keywords matched
    anywhere in the string, which put most Indian namkeen in "whole_foods" (salt limit 0.1 g).
    """
    fallback = "general"
    for fg in reversed(food_groups or []):
        fg = str(fg).strip().lower()
        if fg in _AMBIGUOUS_FOOD_GROUPS:
            fallback = _AMBIGUOUS_FOOD_GROUPS[fg]
            break
        group = _FOOD_GROUP_MAP.get(fg)
        if group:
            return group

    for tag in reversed(_split_tags(categories)):
        if tag in _UNINFORMATIVE_TAGS:
            continue
        group = _group_for_tag(tag)
        if group:
            return group
    return fallback


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
    "milk": ["milk", "milk solids", "lactose", "whey", "casein", "buttermilk", "ghee",
             "paneer", "cheese", "cream", "curd", "yogurt", "yoghurt", "khoa", "khoya", "butter"],
    "peanuts": ["peanut", "groundnut", "monkey nut"],
    "tree_nuts": ["almond", "cashew", "walnut", "pistachio", "hazelnut", "pecan", "macadamia",
                  "brazil nut", "pine nut", "badam", "kaju", "pista", "akhrot", "tree nut"],
    "gluten": ["wheat", "barley", "rye", "gluten", "maida", "suji", "sooji", "semolina",
               "atta", "oat"],
    "soy": ["soy", "soya", "soybean", "edamame", "tofu"],
    "shellfish": ["shrimp", "prawn", "crab", "lobster", "mussel", "oyster", "clam", "crustacean"],
    "fish": ["fish", "anchovy", "salmon", "cod", "tuna", "sardine", "mackerel"],
    "sesame": ["sesame", "til", "gingelly"],
    "mustard": ["mustard", "sarson"],
    "egg": ["egg", "albumin"],
}

# Plant "milks"/"butters" are not dairy.
_NOT_DAIRY = re.compile(r"\b(coconut|almond|soy|soya|oat|rice|cashew|peanut|cocoa|nut|shea)\s+(?:milk|butter|cream)s?\b")

_ALLERGEN_PATTERNS = {
    cat: re.compile(r"\b(?:" + "|".join(re.escape(k) for k in kws) + r")(?:s|es)?\b")
    for cat, kws in COMMON_ALLERGENS.items()
}

# OFF allergen tags → our allergen categories
_OFF_ALLERGEN_MAP = {
    "en:milk": "milk", "en:peanuts": "peanuts", "en:nuts": "tree_nuts", "en:gluten": "gluten",
    "en:soybeans": "soy", "en:crustaceans": "shellfish", "en:molluscs": "shellfish",
    "en:fish": "fish", "en:sesame-seeds": "sesame", "en:mustard": "mustard", "en:eggs": "egg",
}

ALLERGEN_LABELS = {
    "milk": "milk", "peanuts": "peanuts", "tree_nuts": "tree nuts", "gluten": "gluten",
    "soy": "soy", "shellfish": "shellfish", "fish": "fish", "sesame": "sesame",
    "mustard": "mustard", "egg": "egg",
}


def extract_allergens(ingredients_text: str, off_allergens=None) -> list:
    """Whole-word matching ("til" must not match "lentil"), merged with OFF's declared allergens."""
    detected = set()
    if ingredients_text:
        # Keep the plant word ("peanut" is still an allergen), drop the dairy-sounding one
        text = _NOT_DAIRY.sub(r"\1", ingredients_text.lower())
        for allergen_category, pattern in _ALLERGEN_PATTERNS.items():
            if pattern.search(text):
                detected.add(allergen_category)
    if off_allergens:
        tags = off_allergens if isinstance(off_allergens, list) else str(off_allergens).split(",")
        for tag in tags:
            cat = _OFF_ALLERGEN_MAP.get(tag.strip().lower())
            if cat:
                detected.add(cat)
    return sorted(detected)


def user_allergen_categories(user_allergies) -> set:
    """Maps free-text profile allergies ("Dairy", "Tree Nuts", "Peanut allergy") to allergen categories."""
    cats = set()
    for raw in user_allergies or []:
        a = str(raw).lower().strip()
        if not a or a in {"none", "no", "nil", "na", "n/a"}:
            continue
        if "peanut" in a or "groundnut" in a:
            cats.add("peanuts")
        elif "nut" in a or any(k in a for k in ("almond", "cashew", "walnut", "pistachio", "hazelnut")):
            cats.add("tree_nuts")
        if any(k in a for k in ("dairy", "milk", "lactose", "casein", "whey")):
            cats.add("milk")
        if any(k in a for k in ("gluten", "wheat", "celiac", "coeliac")):
            cats.add("gluten")
        if "soy" in a:
            cats.add("soy")
        if any(k in a for k in ("shellfish", "prawn", "shrimp", "crab", "crustacean", "lobster")):
            cats.add("shellfish")
        if "fish" in a and "shellfish" not in a:
            cats.add("fish")
        if "sesame" in a or a == "til":
            cats.add("sesame")
        if "mustard" in a:
            cats.add("mustard")
        if "egg" in a:
            cats.add("egg")
    return cats


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

def _amount_word(value: float, floor: float) -> str:
    """'High' only when well past the limit, so the words match the size of the penalty."""
    return "Very high" if value >= floor * 3 else ("High" if value >= floor * 1.5 else "Slightly high")


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
    # Groups with a 0 g floor (any trans fat is too much) count against 0.1 g instead of dividing by zero,
    # which crashed the whole scan for e.g. crisps or drinks listing 0.1 g.
    ratio = value / max(floor, 0.1)
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
    ingredients_count: int,
    serving_mult: float = 1.0,
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
                    "label": "No added sugar", 
                    "points": round(abs(s_ded * 4.0), 1)
                })
            elif nova_group == 1:
                s_ded = s_ded * 0.5
                bonuses.append({
                    "reason": f"Natural sugar ({sugar:.1f}g/100g) in unprocessed whole food — penalty halved",
                    "label": "Sugar is mostly natural", 
                    "points": round(abs(s_ded), 1)
                })
            
            if s_ded < -0.1:
                deductions.append({
                    "reason": f"Sugar: {sugar:.1f}g/100g (group safe floor: {sugar_floor}g)",
                    "label": f"{_amount_word(sugar, sugar_floor)} sugar ({sugar:.1f} g per 100 g)", 
                    "points": round(s_ded, 1)
                })

        sl_ded = _salt_deduction(salt, salt_floor)
        if sl_ded < -0.1:
            deductions.append({
                "reason": f"Salt: {salt:.2f}g/100g (group safe floor: {salt_floor}g)",
                "label": f"{_amount_word(salt, salt_floor)} salt ({salt:.1f} g per 100 g)", 
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
                    "label": "Fats come mainly from nuts or seeds",
                    "points": round(abs(sf_ded * 4.0), 1)
                })
            elif is_single_ingredient:
                sf_ded = sf_ded * 0.5
                bonuses.append({
                    "reason": "Single-ingredient natural whole fat — saturated fat penalty halved",
                    "label": "Single natural ingredient",
                    "points": round(abs(sf_ded), 1)
                })
                
            if sf_ded < -0.1:
                deductions.append({
                    "reason": f"Saturated fat: {sat_fat:.1f}g/100g (group safe floor: {sat_fat_floor}g)",
                    "label": f"{_amount_word(sat_fat, sat_fat_floor)} saturated fat ({sat_fat:.1f} g per 100 g)", 
                    "points": round(sf_ded, 1)
                })

        tf_ded = _trans_fat_deduction(trans, trans_floor)
        if tf_ded < -0.1:
            deductions.append({
                "reason": f"Trans fat: {trans:.2f}g/100g",
                "label": f"Contains trans fat ({trans:.1f} g per 100 g)", 
                "points": round(tf_ded, 1)
            })

        kd_ded = _kcal_deduction(kcal, kcal_cap)
        if kd_ded < 0:
            if is_nut_or_seed:
                kd_ded = 0.0
                bonuses.append({
                    "reason": "Nutrient-dense whole nuts/seeds — high caloric density penalty waived",
                    "label": "Calorie-dense, but from nuts or seeds",
                    "points": 0.0
                })
            elif is_single_ingredient:
                kd_ded = kd_ded * 0.5
                bonuses.append({
                    "reason": "Single-ingredient natural food — high caloric density penalty halved",
                    "label": "Single natural ingredient",
                    "points": round(abs(kd_ded), 1)
                })
                
            if kd_ded < -0.1:
                deductions.append({
                    "reason": f"High caloric density: {kcal:.0f} kcal/100g",
                    "label": f"Very calorie-dense ({kcal:.0f} kcal per 100 g)", 
                    "points": round(kd_ded, 1)
                })

        # Oils — fat content not penalised
        if group == "oils":
            sf_ded = max(sf_ded, -3.0)  # soften sat fat cap for oils

        fiber_bonus = 6 if fiber >= 8 else (4 if fiber >= 5 else (2 if fiber >= 3 else 0))
        if fiber_bonus:
            bonuses.append({"reason": f"Good fiber: {fiber:.1f}g/100g", "label": f"Good fibre ({fiber:.1f} g per 100 g)", "points": fiber_bonus})

        protein_bonus = 4 if protein >= 25 else (3 if protein >= 15 else (2 if protein >= 8 else 0))
        if protein_bonus:
            bonuses.append({"reason": f"Good protein: {protein:.1f}g/100g", "label": f"Good protein ({protein:.1f} g per 100 g)", "points": protein_bonus})

        # Heuristic 3: Diet Soda / Artificial Sweetener "Chemical Masquerade" Penalty
        sweetener_penalty = 0.0
        if _has_artificial_sweeteners(ingredients_text, additives_tags):
            sweetener_penalty = -10.0
            deductions.append({
                "reason": "Artificially sweetened — chemical masquerade penalty applied",
                "label": "Artificially sweetened", 
                "points": -10.0
            })

        total_ded = s_ded + sl_ded + sf_ded + tf_ded + kd_ded + sweetener_penalty
        score = base + total_ded * serving_mult + fiber_bonus + protein_bonus

    else:
        # Fallback: use nutrient_levels dict (high/moderate/low)
        base = 15.0
        total_ded = 0.0
        level_map = {"high": -4.0, "moderate": -2.0, "low": 0.0}
        for nutrient_key in ["sugars", "fat", "salt", "saturated-fat"]:
            level = nutrient_levels.get(nutrient_key, "")
            ded = level_map.get(level, 0.0)
            if ded:
                deductions.append({
                    "reason": f"{nutrient_key} level: {level} (from nutrient_levels, no exact data)",
                    "label": f"{'High' if level == 'high' else 'Moderate'} {nutrient_key.replace('-', ' ')}",
                    "points": ded
                })
                total_ded += ded
        score = base + total_ded * serving_mult

    # Serving size scales the penalties only. (It used to be applied afterwards by re-adding every
    # bonus entry, including display-only ones, which inflated scores for small and large packs.)
    if serving_mult != 1.0:
        for d in deductions:
            d["points"] = round(d["points"] * serving_mult, 1)

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
                bonuses.append({"reason": f"{label}: {val:.2f} (≥ 15% RDA per 100g)", "label": f"Source of {label}", "points": pts})

    if fortified_and_ultra and bonus == 0 and any(
        (nutrients.get(k, 0) or 0) >= t for k, t, _ in _MICRONUTRIENTS
    ):
        bonuses.append({"reason": "Micronutrients industrially added to ultra-processed food — no credit awarded", "points": 0})

    return min(5, bonus), bonuses


# ---------------------------------------------------------------------------
# Pillar 2 — Processing Level (NOVA)
# ---------------------------------------------------------------------------

_NOVA_SCORES = {1: 25, 2: 20, 3: 12, 4: 0}

def _score_processing(nova_group: Optional[int], additives_count: int,
                      has_ingredients: bool = True) -> tuple[int, list, list]:
    deductions, bonuses = [], []
    if nova_group is None and not has_ingredients:
        # No NOVA and no ingredient list: zero additives means "unknown", not "clean".
        return 10, [], []
    if nova_group is not None:
        score = _NOVA_SCORES.get(nova_group, 10)
        label = {1: "Unprocessed/minimally processed",
                 2: "Processed culinary ingredient",
                 3: "Processed food",
                 4: "Ultra-processed food"}
        if nova_group == 4:
            deductions.append({"reason": f"Ultra-processed food (NOVA 4) — maximum processing penalty", "label": "Ultra-processed", "points": -25})
        elif nova_group == 3:
            deductions.append({"reason": f"Processed food (NOVA 3)", "label": "Processed food", "points": -13})
        else:
            bonuses.append({"reason": f"{label.get(nova_group, 'NOVA ' + str(nova_group))} (NOVA {nova_group})", "label": "Minimally processed" if nova_group == 1 else "Lightly processed", "points": score})
        return score, deductions, bonuses

    # Estimate from additive count
    if additives_count == 0:
        score = 18
        bonuses.append({"reason": "No additives detected — likely minimally processed (estimated)", "label": "No additives", "points": 18})
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
            deductions.append({"reason": f"{code.upper()} — high-risk additive (artificial color/preservative/sweetener)", "label": f"Additive of concern ({code.upper()})", "points": pts})
            base += pts
        elif code in _ORANGE_ADDITIVES:
            pts = -3
            if is_condiment and small_serving:
                pts = -2
            deductions.append({"reason": f"{code.upper()} — moderate-risk additive", "label": f"Additive to limit ({code.upper()})", "points": pts})
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
        deductions.append({"reason": f"High ingredient count ({count}) — indicator of complexity/processing", "label": f"Long ingredient list ({count} items)", "points": d})
        base += d

    # Red flag scan (max -4 total from flags)
    flag_total = 0.0
    for phrase, pts in _RED_FLAG_INGREDIENTS:
        if phrase in text_lower and flag_total > -4.0:
            deductions.append({"reason": f"Contains '{phrase}'", "label": f"Contains {phrase}", "points": pts})
            base += pts
            flag_total += pts

    # Refined / Inflammatory Oils Scan (max -4.0)
    oil_ded = 0.0
    detected_oils = []
    if any(p in text_lower for p in ["palm oil", "palm olein", "palmolein", "palm fat", "palm kernel", "hydrogenated palm"]):
        oil_ded = -4.0
        detected_oils.append("palm oil/olein")
    elif any(p in text_lower for p in ["soybean oil", "soy oil", "vegetable oil", "canola oil", "rapeseed oil", "sunflower oil", "corn oil", "cottonseed oil"]):
        oil_ded = -2.0
        detected_oils.append("refined vegetable oil")
    if oil_ded < 0.0:
        deductions.append({
            "reason": f"Contains refined inflammatory oil ({', '.join(detected_oils)})",
            "label": "Made with palm oil" if "palm oil/olein" in detected_oils else "Made with refined vegetable oil", 
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
            "label": f"Contains {' and '.join(detected_fillers)}", 
            "points": filler_ded
        })
        base += filler_ded

    # Artificial Sweetener scanner in Pillar 5 (-3.0)
    if _has_artificial_sweeteners(ingredients_text, additives_tags):
        deductions.append({
            "reason": "Contains artificial/synthetic sweetener (integrity penalty)",
            "label": "Artificially sweetened", 
            "points": -3.0
        })
        base += -3.0

    # Sugar-first penalty
    if ingredients:
        first = ingredients[0].lower()
        if any(term in first for term in _SUGAR_FIRST_TERMS):
            deductions.append({"reason": "Sugar/sweetener is the primary ingredient (listed first by weight)", "label": "Sugar is the main ingredient", "points": -2.0})
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

# One scale everywhere: the verdict and the frequency come from the same score bands, so the app
# can no longer say "AVOID" and "fine weekly" about the same product.
VERDICT_BANDS = [
    # min score, verdict, frequency
    (80, "GREAT", "Fine to eat daily"),
    (65, "GOOD",  "3–4 times a week"),
    (50, "OKAY",  "Once or twice a week"),
    (35, "LIMIT", "Once or twice a month"),
    (0,  "AVOID", "Only on rare occasions"),
]


def verdict_for(score: int) -> str:
    return next(v for floor, v, _ in VERDICT_BANDS if score >= floor)


def _frequency(score: int) -> str:
    return next(f for floor, _, f in VERDICT_BANDS if score >= floor)


def explain(result: "ScoringResult", product_name: Optional[str] = None,
            nutrients: Optional[dict] = None) -> dict:
    """
    Plain-language explanation built only from the score breakdown. Used whenever the LLM is
    unavailable, so users never see "AI explanation unavailable".
    """
    nutrients = nutrients or {}
    name = (product_name or "This product").strip()
    concerns, positives = result.concerns, result.positives

    if concerns and positives:
        reason = f"Main concerns: {_join(concerns[:3]).lower()}. On the plus side: {_join(positives[:2]).lower()}."
    elif concerns:
        reason = f"Main concerns: {_join(concerns[:3]).lower()}."
    elif positives:
        reason = f"Good points: {_join(positives[:3]).lower()}, with no major concerns."
    else:
        reason = "No major concerns or standout benefits in the available data."

    category = CATEGORY_LABELS.get(result.category_group, "packaged foods")
    summary = (f"{name} was compared with other {category}. "
               f"Suggested amount: {result.safe_consumption_frequency.lower()}.")

    kcal = nutrients.get("energy-kcal_100g")
    if kcal is None and nutrients.get("energy_100g"):
        kcal = nutrients["energy_100g"] / 4.184
    if kcal is not None:
        density = "high" if kcal >= 400 else ("moderate" if kcal >= 150 else "low")
        energy = f"{kcal:.0f} kcal per 100 g, which is {density} for its weight."
    else:
        energy = "Calories per 100 g are not listed for this product."

    parts = []
    for key, label in (("carbohydrates_100g", "carbs"), ("proteins_100g", "protein"),
                       ("fat_100g", "fat"), ("fiber_100g", "fibre")):
        v = nutrients.get(key)
        if v is not None:
            parts.append(f"{float(v):.1f} g {label}")
    macro = f"Per 100 g: {_join(parts)}." if parts else "Macronutrients are not listed for this product."

    return {
        "health_reason": reason,
        "summary": summary,
        "nutrition_analysis": {"energy_estimation": energy, "macronutrient_balance": macro},
    }


def _join(items: list) -> str:
    items = [i for i in items if i]
    if len(items) <= 1:
        return "".join(items)
    return ", ".join(items[:-1]) + " and " + items[-1]


def _top_labels(entries: list, limit: int, negative: bool) -> list:
    """Largest effects first, one label per kind (several additives collapse into one line)."""
    numeric = [e for e in entries if e.get("label") and isinstance(e.get("points"), (int, float))]
    capped = [e for e in entries if e.get("label") and e.get("points") == "cap"]
    numeric.sort(key=lambda e: e["points"] if negative else -e["points"])
    seen, out = set(), []
    for e in capped + numeric:
        label = e["label"]
        if negative and e.get("points") not in ("cap",) and e["points"] >= 0:
            continue
        if not negative and e["points"] <= 0:
            continue
        key = label.split(" (")[0]
        if key.startswith("Additive"):
            key = "Additive"
        if key in seen:
            continue
        seen.add(key)
        out.append(label)
        if len(out) >= limit:
            break
    return out


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
    food_groups: Optional[list] = None,
    off_allergens=None,
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
    if isinstance(categories, list):
        categories = ",".join(categories)

    group = detect_category_group(categories, food_groups)
    serving_grams = _parse_serving_grams(serving_size)

    all_deductions, all_bonuses = [], []

    # ── Alcohol override (hard cap) ─────────────────────────────────────────
    alcohol = nutrients.get("alcohol_100g", 0) or 0
    alcohol_capped = alcohol > 0.5

    # Detect nut/seed/dried fruit
    # Only for actual nut/seed/dried-fruit products: "Good Day Cashew" is a biscuit, not cashews.
    is_nut_or_seed = group in ("whole_foods", "snacks", "general") and _is_nut_seed_or_dried_fruit(
        product_name, categories, ingredients_text)
    if is_nut_or_seed and group != "whole_foods" and ingredients:
        is_nut_or_seed = any(k in str(ingredients[0]).lower() for k in _NUT_SEED_KEYWORDS)

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
        serving_mult=_serving_multiplier(serving_grams),
    )

    all_deductions.extend(p1_ded)
    all_bonuses.extend(p1_bon)

    # ── Pillar 1 addition: Micronutrients ───────────────────────────────────
    micro_bonus, micro_bon = _score_micronutrients(nutrients, ingredients_text, nova_group)
    all_bonuses.extend(micro_bon)

    # ── Pillar 2: Processing ────────────────────────────────────────────────
    p2, p2_ded, p2_bon = _score_processing(nova_group, len(additives_tags), bool(ingredients_text.strip()))
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
    # Glycemic load is defined per portion, not per 100 g (per 100 g it flagged oats and atta like
    # candy). Use the pack's serving size, or a typical 50 g portion when none is given.
    carbs = nutrients.get("carbohydrates_100g", 0) or 0
    portion_g = serving_grams if serving_grams and 5 <= serving_grams <= 300 else 50.0
    gi = get_gi_estimate(product_name, group)
    gl = calculate_glycemic_load(carbs * portion_g / 100.0, gi)

    # Penalize if GL is very high (above 20 per portion is considered high)
    if gl > 20.0:
        gl_penalty = min(10.0, (gl - 20.0) / 2.0)
        all_deductions.append({
            "reason": f"High Glycemic Load ({gl:.1f}) — rapid blood sugar spike",
            "label": "Likely to spike blood sugar",
            "points": -gl_penalty
        })
        p1_base -= gl_penalty # apply penalty to macronutrient pillar for now

    detected_allergens = extract_allergens(ingredients_text, off_allergens)
    allergen_warnings = [f"Contains {ALLERGEN_LABELS.get(a, a)}" for a in detected_allergens]

    # ── Total ───────────────────────────────────────────────────────────────
    raw = p1_base + micro_bonus + p2 + p3 + p4 + p5

    if alcohol_capped:
        raw = min(raw, 40.0)
        all_deductions.insert(0, {
            "reason": f"Contains alcohol ({alcohol:.1f}g/100g) — score hard-capped at 40",
            "label": "Contains alcohol",
            "points": "cap"
        })

    # Hard cap for artificial sweeteners (Diet Soda Loophole Fix)
    has_sweeteners = _has_artificial_sweeteners(ingredients_text, additives_tags)
    if has_sweeteners:
        raw = min(raw, 65.0)
        all_deductions.insert(0, {
            "reason": "Contains artificial/synthetic sweeteners — score hard-capped at 65 to prevent chemical masquerading",
            "label": "Artificially sweetened",
            "points": "cap"
        })

    # Missing ingredients penalty
    if not ingredients_text:
        raw = min(raw, 75.0)
        all_deductions.insert(0, {
            "reason": "Missing ingredient data — score penalized and capped at 75",
            "label": "Ingredient list not available",
            "points": -10.0
        })
        raw = max(0.0, raw - 10.0)

    health_score = int(max(0.0, min(100.0, raw)))

    confidence = _compute_confidence(
        nutrients, nutrient_levels, nova_group,
        additives_tags, nutri_score, ingredients_text
    )

    visible_deductions = [d for d in all_deductions if d.get("points") != 0]
    return ScoringResult(
        health_score=health_score,
        verdict=verdict_for(health_score),
        is_good_for_health=health_score >= 65,
        # Kept for older app builds; the schema requires 1–10.
        health_scale=max(1.0, round(health_score / 10.0, 1)),
        safe_consumption_frequency=_frequency(health_score),
        pillar_scores={
            "macronutrient":        p1_base,
            "micronutrient_bonus":  micro_bonus,
            "processing":           p2,
            "additive_safety":      p3,
            "nutri_score_pts":      p4,
            "ingredient_integrity": p5,
        },
        deductions=visible_deductions,
        bonuses=all_bonuses,
        data_confidence=confidence,
        category_group=group,
        alcohol_capped=alcohol_capped,
        glycemic_load=gl,
        allergens=detected_allergens,
        allergen_warnings=allergen_warnings,
        concerns=_top_labels(visible_deductions, 4, negative=True),
        positives=_top_labels(all_bonuses, 3, negative=False),
    )
