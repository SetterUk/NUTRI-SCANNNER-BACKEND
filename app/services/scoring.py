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
                      "nut", "seed", "grain", "oat", "quinoa", "rice", "pulse"],
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
    for group, keywords in _CATEGORY_KEYWORDS.items():
        if any(k in cat for k in keywords):
            return group
    return "general"


# ---------------------------------------------------------------------------
# Category-specific sugar/salt/sat-fat floors (g per 100g)
# Above 1× floor → -3, 3× → -6, 6× → -9, 9× → -12
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

def _sugar_deduction(value: float, floor: float) -> int:
    if value > floor * 9:  return -12
    if value > floor * 6:  return -9
    if value > floor * 3:  return -6
    if value > floor * 1:  return -3
    return 0

def _salt_deduction(value: float, floor: float) -> int:
    if value > floor * 3.3:  return -6
    if value > floor * 2:    return -4
    if value > floor:        return -2
    return 0

def _sat_fat_deduction(value: float, floor: float, is_protein_food: bool) -> int:
    raw = 0
    if value > floor * 7.5:  raw = -8
    elif value > floor * 5:  raw = -6
    elif value > floor * 2.5:raw = -4
    elif value > floor:      raw = -2
    if is_protein_food:
        raw = int(raw * 0.6)  # natural meat fat is less harmful
    return raw

def _trans_fat_deduction(value: float, floor: float) -> int:
    if value > floor * 4:  return -8
    if value > floor * 2:  return -5
    if value > floor:      return -2
    return 0

def _kcal_deduction(value: float, cap: int) -> int:
    if value > cap:         return -4
    if value > cap * 0.8:   return -2
    return 0


# ---------------------------------------------------------------------------
# Pillar 1 — Macronutrient Profile
# ---------------------------------------------------------------------------

def _score_macronutrients(nutrients: dict, group: str, nova_group: Optional[int],
                           nutrient_levels: dict) -> tuple[int, list, list]:
    """Returns (score, deductions, bonuses). Score range: 0–30."""
    floors = _THRESHOLDS.get(group, _THRESHOLDS["general"])
    sugar_floor, salt_floor, sat_fat_floor, trans_floor, kcal_cap = floors
    is_protein = (group == "protein_foods")

    # Detect data availability
    key_count = sum(1 for k in ["sugars_100g", "fat_100g", "proteins_100g",
                                 "salt_100g", "fiber_100g", "energy-kcal_100g"]
                    if k in nutrients)

    deductions, bonuses = [], []

    if key_count >= 2:
        base = 30
        sugar  = nutrients.get("sugars_100g", 0) or 0
        salt   = nutrients.get("salt_100g") or (nutrients.get("sodium_100g", 0) or 0) * 2.5
        sat_fat= nutrients.get("saturated-fat_100g", 0) or 0
        trans  = nutrients.get("trans-fat_100g", 0) or 0
        kcal   = nutrients.get("energy-kcal_100g") or (nutrients.get("energy_100g", 0) or 0) / 4.184
        fiber  = nutrients.get("fiber_100g", 0) or 0
        protein= nutrients.get("proteins_100g", 0) or 0

        # Sugar — may be corrected later for NOVA 1
        s_ded = _sugar_deduction(sugar, sugar_floor)
        if s_ded < 0:
            # Natural sugar correction: NOVA 1 whole food — halve penalty
            if nova_group == 1 and sugar > sugar_floor * 3:
                s_ded = s_ded // 2
                bonuses.append({"reason": f"Natural sugar ({sugar:.1f}g/100g) in unprocessed food — penalty halved", "points": abs(s_ded)})
            deductions.append({"reason": f"Sugar: {sugar:.1f}g/100g (group safe floor: {sugar_floor}g)", "points": s_ded})

        sl_ded = _salt_deduction(salt, salt_floor)
        if sl_ded < 0:
            deductions.append({"reason": f"Salt: {salt:.2f}g/100g (group safe floor: {salt_floor}g)", "points": sl_ded})

        sf_ded = _sat_fat_deduction(sat_fat, sat_fat_floor, is_protein)
        if sf_ded < 0:
            deductions.append({"reason": f"Saturated fat: {sat_fat:.1f}g/100g (group safe floor: {sat_fat_floor}g)", "points": sf_ded})

        tf_ded = _trans_fat_deduction(trans, trans_floor)
        if tf_ded < 0:
            deductions.append({"reason": f"Trans fat: {trans:.2f}g/100g", "points": tf_ded})

        kd_ded = _kcal_deduction(kcal, kcal_cap)
        if kd_ded < 0:
            deductions.append({"reason": f"High caloric density: {kcal:.0f} kcal/100g", "points": kd_ded})

        # Oils — fat content not penalised
        if group == "oils":
            sf_ded = max(sf_ded, -3)  # soften sat fat cap for oils

        fiber_bonus = 6 if fiber >= 8 else (4 if fiber >= 5 else (2 if fiber >= 3 else 0))
        if fiber_bonus:
            bonuses.append({"reason": f"Good fiber: {fiber:.1f}g/100g", "points": fiber_bonus})

        protein_bonus = 4 if protein >= 25 else (3 if protein >= 15 else (2 if protein >= 8 else 0))
        if protein_bonus:
            bonuses.append({"reason": f"Good protein: {protein:.1f}g/100g", "points": protein_bonus})

        total_ded = s_ded + sl_ded + sf_ded + tf_ded + kd_ded
        score = base + total_ded + fiber_bonus + protein_bonus

    else:
        # Fallback: use nutrient_levels dict (high/moderate/low)
        base = 15  # data penalty
        score = base
        level_map = {"high": -4, "moderate": -2, "low": 0}
        for nutrient_key in ["sugars", "fat", "salt", "saturated-fat"]:
            level = nutrient_levels.get(nutrient_key, "")
            ded = level_map.get(level, 0)
            if ded:
                deductions.append({"reason": f"{nutrient_key} level: {level} (from nutrient_levels, no exact data)", "points": ded})
                score += ded

    return max(0, min(30, score)), deductions, bonuses


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

def _score_ingredient_integrity(ingredients: list, ingredients_text: str) -> tuple[int, list, list]:
    base = 10
    deductions, bonuses = [], []
    text_lower = ingredients_text.lower()

    # Count penalty
    count = len(ingredients)
    if count > 30:
        d = -4
    elif count > 20:
        d = -3
    elif count > 10:
        d = -2
    elif count > 5:
        d = -1
    else:
        d = 0
    if d:
        deductions.append({"reason": f"High ingredient count ({count}) — indicator of complexity/processing", "points": d})
        base += d

    # Red flag scan (max -4 total from flags)
    flag_total = 0
    for phrase, pts in _RED_FLAG_INGREDIENTS:
        if phrase in text_lower and flag_total > -4:
            deductions.append({"reason": f"Contains '{phrase}'", "points": pts})
            base += pts
            flag_total += pts

    # Sugar-first penalty
    if ingredients:
        first = ingredients[0].lower()
        if any(term in first for term in _SUGAR_FIRST_TERMS):
            deductions.append({"reason": "Sugar/sweetener is the primary ingredient (listed first by weight)", "points": -2})
            base -= 2

    return max(0, min(10, base)), deductions, bonuses


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

    # ── Pillar 1: Macronutrients ────────────────────────────────────────────
    p1_base, p1_ded, p1_bon = _score_macronutrients(
        nutrients, group, nova_group, nutrient_levels
    )

    # Apply serving size multiplier to Pillar 1 deductions only
    srv_mult = _serving_multiplier(serving_grams)
    if srv_mult != 1.0:
        adjusted_p1_ded = []
        for d in p1_ded:
            new_pts = int(d["points"] * srv_mult)
            adjusted_p1_ded.append({
                **d,
                "points": new_pts,
                "note": f"adjusted for serving size ({serving_grams:.0f}g)" if srv_mult < 1 else None
            })
        p1_ded = adjusted_p1_ded
        p1_base_adjusted = 30 + sum(d["points"] for d in p1_ded) + sum(b["points"] for b in p1_bon)
        p1_base = max(0, min(30, p1_base_adjusted))

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
    p5, p5_ded, p5_bon = _score_ingredient_integrity(ingredients, ingredients_text)
    all_deductions.extend(p5_ded)
    all_bonuses.extend(p5_bon)

    # ── Total ───────────────────────────────────────────────────────────────
    raw = p1_base + micro_bonus + p2 + p3 + p4 + p5

    if alcohol_capped:
        raw = min(raw, 40)
        all_deductions.insert(0, {
            "reason": f"Contains alcohol ({alcohol:.1f}g/100g) — score hard-capped at 40",
            "points": "cap"
        })

    health_score = max(0, min(100, raw))

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
        bonuses=[b for b in all_bonuses if b.get("points", 1) != 0],
        data_confidence=confidence,
        category_group=group,
        alcohol_capped=alcohol_capped,
    )
