"""
Deterministic per-user safety checks (allergies and diet). These never depend on the LLM:
a missed allergen is the worst mistake this app can make.
"""
import re
from typing import Optional

from app.services.scoring import ALLERGEN_LABELS, user_allergen_categories

_MEAT_FISH = re.compile(
    r"\b(?:meat|chicken|mutton|beef|pork|lamb|fish|prawn|shrimp|crab|gelatine?|lard|anchovy|tuna|"
    r"salmon|sardine|bacon|ham|sausage)s?\b"
)
_EGG = re.compile(r"\b(?:egg|eggs|albumin)\b")
_DAIRY = re.compile(r"\b(?:milk|ghee|butter|paneer|cheese|cream|curd|yogurt|whey|casein|khoa|khoya|lactose)s?\b")
_JAIN_EXCLUDED = re.compile(r"\b(?:onion|garlic|potato|carrot|beetroot|radish)(?:s|es)?\b")
_PLANT_MILK = re.compile(r"\b(?:coconut|almond|soy|soya|oat|rice|cashew|peanut|cocoa|nut|shea)\s+(?:milk|butter|cream)s?\b")


def _diet(profile_ctx: dict) -> str:
    return (profile_ctx.get("dietary_preferences") or "").strip().lower()


def personal_alerts(allergens: list, diet_tags: Optional[list], ingredients_text: Optional[str],
                    profile_ctx: Optional[dict]) -> list:
    """Plain-language reasons this product is unsuitable for this user. Empty list = no conflict."""
    if not profile_ctx:
        return []
    alerts = []
    text = (ingredients_text or "").lower()
    diet_tags = [t.lower() for t in (diet_tags or [])]

    user_cats = user_allergen_categories(profile_ctx.get("allergies"))
    diet = _diet(profile_ctx)
    if "gluten" in diet:
        user_cats.add("gluten")
    for cat in sorted(user_cats & set(allergens or [])):
        alerts.append(f"Contains {ALLERGEN_LABELS.get(cat, cat)}, which is in your allergies")

    non_veg = "en:non-vegetarian" in diet_tags or bool(_MEAT_FISH.search(text))
    has_egg = "egg" in (allergens or []) or bool(_EGG.search(text))
    if diet in ("vegetarian", "jain", "vegan", "eggitarian", "eggetarian") and non_veg:
        alerts.append("Not vegetarian")
    elif diet in ("vegetarian", "jain", "vegan") and has_egg:
        alerts.append("Contains egg")
    if diet == "vegan" and not non_veg:
        if "en:non-vegan" in diet_tags or _DAIRY.search(_PLANT_MILK.sub(" ", text)) or "honey" in text:
            alerts.append("Not vegan")
    if diet == "jain":
        m = _JAIN_EXCLUDED.search(text)
        if m:
            alerts.append(f"Contains {m.group(0)}, which a Jain diet excludes")
    return alerts


def apply_personal_alerts(analysis: dict, alerts: list) -> dict:
    """Overrides the verdict when the product is unsafe or off-diet for this user. The score is kept:
    it describes the product, and the alert says why this user should skip it anyway."""
    out = dict(analysis)
    out["personal_alerts"] = alerts
    if alerts:
        out["verdict"] = "AVOID"
        out["is_good_for_health"] = False
        out["safe_consumption_frequency"] = "Not suitable for you"
    return out
