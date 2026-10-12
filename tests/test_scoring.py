import pytest

from app.services.scoring import (
    compute_health_score, detect_category_group, explain, extract_allergens,
    user_allergen_categories, verdict_for, _frequency,
)

CHIWDA_NUTRIENTS = {
    "carbohydrates_100g": 37.8, "energy-kcal_100g": 587, "energy_100g": 2456, "fat_100g": 42,
    "fiber_100g": 4.5, "proteins_100g": 14.1, "salt_100g": 0.55, "saturated-fat_100g": 8.6,
    "sugars_100g": 10,
}
CHIWDA_TEXT = ("Potato, Refined Palmolein Oil, Refined Sugar, Rock Salt, Red Chilli, Black Pepper, "
               "Poppy Seed, Cumin. Allergen: Contains Peanuts, Almond, Cashew, Milk & Sesame Seed")


def score(**overrides):
    args = dict(
        nutrients=CHIWDA_NUTRIENTS, nova_group=3, nutri_score="d", additives_tags=[],
        nutrient_levels={}, ingredients=CHIWDA_TEXT.split(","), ingredients_text=CHIWDA_TEXT,
        categories="en:plant-based-foods-and-beverages,en:plant-based-foods,en:snacks,en:salty-snacks",
        serving_size="200g", product_name="Farali Chiwda",
    )
    args.update(overrides)
    return compute_health_score(**args)


@pytest.mark.parametrize("categories,expected", [
    # The most specific tag decides (OFF lists broad → specific)
    ("en:snacks,en:sweet-snacks,en:biscuits-and-cakes,en:biscuits", "grains_bakery"),
    ("en:plant-based-foods-and-beverages,en:plant-based-foods,en:snacks,en:salty-snacks", "snacks"),
    ("en:snacks,en:salty-snacks,en:crisps,en:potato-crisps", "snacks"),
    ("en:dairies,en:milks,en:low-fat-milks", "dairy"),
    ("en:beverages,en:plant-based-beverages,en:coconut-water", "beverages"),
    ("en:sweet-snacks,en:confectioneries,en:chocolate-candies,en:milk-chocolates", "confectionery"),
    ("en:groceries,en:sauces,en:tomato-sauces", "condiments"),
    ("en:plant-based-foods,en:legumes,en:pulses,en:lentils", "whole_foods"),
    # Head noun wins inside one tag
    ("en:chocolate-biscuits", "grains_bakery"),
    ("en:ice-creams", "confectionery"),
    # Whole words only: these used to match "nut", "egg" and "oat"
    ("en:coconut-cookies", "grains_bakery"),
    ("en:veggie-burgers", "general"),
    ("en:coated-wafers", "grains_bakery"),
    # Indian names
    ("en:snacks,en:namkeen", "snacks"),
    ("en:bhujia", "snacks"),
    # Broad-only or empty
    ("en:plant-based-foods-and-beverages", "general"),
    (None, "general"),
    ("", "general"),
])
def test_category_detection(categories, expected):
    assert detect_category_group(categories) == expected


def test_food_groups_take_priority_over_categories():
    assert detect_category_group("en:snacks", ["en:sugary-snacks", "en:biscuits-and-cakes"]) == "grains_bakery"
    assert detect_category_group("en:foods", ["en:salty-snacks"]) == "snacks"


def test_chiwda_salt_is_not_called_high_for_a_snack():
    r = score()
    assert r.category_group == "snacks"
    assert not any("salt" in c.lower() for c in r.concerns)
    assert any("palm oil" in c.lower() for c in r.concerns) or any("saturated fat" in c.lower() for c in r.concerns)


@pytest.mark.parametrize("value,verdict", [(100, "GREAT"), (80, "GREAT"), (79, "GOOD"), (65, "GOOD"),
                                           (64, "OKAY"), (50, "OKAY"), (49, "LIMIT"), (35, "LIMIT"),
                                           (34, "AVOID"), (0, "AVOID")])
def test_verdict_bands(value, verdict):
    assert verdict_for(value) == verdict


def test_verdict_and_frequency_agree():
    # 57 used to show "AVOID" next to "Weekly"
    assert verdict_for(57) == "OKAY"
    assert _frequency(57) == "Once or twice a week"


def test_very_low_score_does_not_break_schema():
    from app.models.schemas import AIAnalysisResult
    r = score(nutrients={"sugars_100g": 90, "salt_100g": 5, "saturated-fat_100g": 30, "energy-kcal_100g": 900},
              nova_group=4, nutri_score="e", additives_tags=["en:e102", "en:e110", "en:e951", "en:e621"])
    assert r.health_score < 10
    assert r.verdict == "AVOID"
    AIAnalysisResult(verdict=r.verdict, health_scale=r.health_scale, health_score=r.health_score)


def test_explanation_never_mentions_unavailable():
    r = score()
    text = explain(r, "Farali Chiwda", CHIWDA_NUTRIENTS)
    joined = " ".join([text["health_reason"], text["summary"], *text["nutrition_analysis"].values()]).lower()
    assert "unavailable" not in joined
    assert "587 kcal" in joined
    assert "savoury snacks" in joined


@pytest.mark.parametrize("text,expected,absent", [
    ("Lentils, distilled vinegar", set(), {"sesame"}),
    ("Coconut milk, cocoa butter, peanut butter", {"peanuts"}, {"milk"}),
    ("Wheat flour (maida), sugar, milk solids", {"gluten", "milk"}, set()),
    ("Almond, til seeds", {"tree_nuts", "sesame"}, set()),
    ("Eggless cake mix", set(), {"egg"}),
])
def test_allergen_detection(text, expected, absent):
    found = set(extract_allergens(text))
    assert expected <= found
    assert not (absent & found)


def test_off_allergen_tags_are_merged():
    assert set(extract_allergens("", "en:gluten,en:milk,en:nuts")) == {"gluten", "milk", "tree_nuts"}


def test_user_allergy_text_mapping():
    assert user_allergen_categories(["Peanuts", "Dairy"]) == {"peanuts", "milk"}
    assert user_allergen_categories(["Tree nuts"]) == {"tree_nuts"}
    assert user_allergen_categories(["None"]) == set()
    assert user_allergen_categories(["Shellfish"]) == {"shellfish"}
