import asyncio
from types import SimpleNamespace

import pytest

from app.services import alternatives as alt_mod
from app.services.personal import personal_alerts, apply_personal_alerts


def test_allergy_alert_overrides_verdict():
    alerts = personal_alerts(["peanuts", "milk"], [], "Peanuts, salt", {"allergies": ["Peanuts"]})
    assert alerts == ["Contains peanuts, which is in your allergies"]
    out = apply_personal_alerts({"verdict": "GREAT", "health_score": 90}, alerts)
    assert out["verdict"] == "AVOID" and out["health_score"] == 90
    assert out["safe_consumption_frequency"] == "Not suitable for you"


def test_no_profile_no_alerts():
    assert personal_alerts(["peanuts"], [], "Peanuts", None) == []
    out = apply_personal_alerts({"verdict": "GOOD"}, [])
    assert out["verdict"] == "GOOD" and out["personal_alerts"] == []


@pytest.mark.parametrize("diet,text,tags,expected", [
    ("Vegetarian", "Chicken, salt", [], "Not vegetarian"),
    ("Vegetarian", "Wheat, egg", [], "Contains egg"),
    ("Eggitarian", "Wheat, egg", [], None),
    ("Vegan", "Sugar, milk solids", [], "Not vegan"),
    ("Vegan", "Coconut milk, sugar", [], None),
    ("Jain", "Potato, salt", [], "Contains potato, which a Jain diet excludes"),
    ("Vegetarian", "Gelatin, sugar", ["en:non-vegetarian"], "Not vegetarian"),
    ("Gluten-Free", "Wheat flour", [], "Contains gluten, which is in your allergies"),
])
def test_diet_alerts(diet, text, tags, expected):
    from app.services.scoring import extract_allergens
    alerts = personal_alerts(extract_allergens(text), tags, text, {"dietary_preferences": diet})
    if expected is None:
        assert alerts == []
    else:
        assert expected in alerts


def _raw(name, sugar, salt, nova=3, allergens="", grade="b", text="Roasted chana, spices"):
    """Shape returned by openfoodfacts.get_product_from_api()."""
    return {
        "name": name, "brand": "Brand", "image_small_url": None, "nutri_score": grade,
        "nutrients": {"energy-kcal_100g": 400, "sugars_100g": sugar, "salt_100g": salt,
                      "saturated-fat_100g": 1, "fat_100g": 10, "proteins_100g": 12, "fiber_100g": 6},
        "nova_group": nova, "additives_tags": [], "nutrient_levels": {},
        "ingredients_text": text, "ingredients_tags": text.split(","),
        "categories": "en:snacks,en:salty-snacks", "food_groups": [], "serving_size": "30g",
        "allergens": allergens, "diet_tags": [],
    }


def test_alternatives_are_better_and_filtered(monkeypatch):
    catalogue = {
        "1": _raw("Roasted chana", sugar=1, salt=0.5),
        "2": _raw("Peanut chikki snack", sugar=1, salt=0.5, allergens="en:peanuts"),
        "3": _raw("Salty fried mix", sugar=30, salt=4, nova=4, grade="e", text="Sugar, palmolein oil, salt"),
        "999": _raw("The scanned product", sugar=1, salt=0.5),
    }

    async def fake_search(tag, exclude_code, page_size=30):
        return [{"code": c, "nutri_score": "b"} for c in catalogue]

    async def fake_fetch(code, retries=1):
        return catalogue.get(code)

    monkeypatch.setattr(alt_mod, "search_category_candidates", fake_search)
    monkeypatch.setattr(alt_mod, "get_product_from_api", fake_fetch)
    product = SimpleNamespace(barcode="999", categories="en:snacks,en:salty-snacks,en:namkeen",
                              nutrients={"sugars_100g": 10, "salt_100g": 2.0, "saturated-fat_100g": 9,
                                         "proteins_100g": 5, "fiber_100g": 1}, nova_group=4)
    found = asyncio.run(alt_mod.find_alternatives(product, current_score=30))
    codes = [a["code"] for a in found]
    assert "1" in codes and "3" not in codes and "999" not in codes
    assert "Less salt" in found[0]["why_better"]

    public = alt_mod.filter_for_user(found, {"allergies": ["Peanuts"]})
    assert [a["code"] for a in public] == ["1"]
    assert set(public[0]) == {"code", "name", "brand", "image_url", "nutri_score", "health_score",
                              "verdict", "why_better"}


def test_alternative_search_tags_skip_broad_ones():
    tags = alt_mod._search_tags("en:plant-based-foods-and-beverages,en:plant-based-foods,en:snacks,"
                                "en:salty-snacks,en:namkeen")
    assert tags == ["en:namkeen", "en:salty-snacks"]
    assert alt_mod._search_tags(None) == []
