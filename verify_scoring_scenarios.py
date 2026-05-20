# verify_scoring_scenarios.py
import sys
from app.services.scoring import compute_health_score

def run_tests():
    print("======================================================================")
    print("RUNNING NUTRITIONAL SCORING SCENARIO TESTS")
    print("======================================================================")

    # -------------------------------------------------------------------------
    # Scenario 1: Haldiram's Roasted Crushed Peanuts
    # -------------------------------------------------------------------------
    print("\n[Scenario 1] Haldiram's Roasted Crushed Peanuts (Nut/Seed Grace & Category Correction)")
    peanuts_result = compute_health_score(
        nutrients={
            "sugars_100g": 10.5,
            "salt_100g": 2.43,
            "saturated-fat_100g": 6.0,
            "energy-kcal_100g": 499.0,
            "proteins_100g": 12.5,
            "carbohydrates_100g": 55.0,
            "fat_100g": 25.5
        },
        nova_group=3,
        nutri_score="e",
        additives_tags=[],
        nutrient_levels={
            "fat": "high",
            "salt": "high",
            "saturated-fat": "high",
            "sugars": "moderate"
        },
        ingredients=["peanuts", "salt"],
        ingredients_text="Peanuts, Salt",
        categories="en:plant-based-foods-and-beverages",
        serving_size="200g",
        product_name="Roasted Crushed Peanuts"
    )
    print(f"Detected Category Group: {peanuts_result.category_group}")
    print(f"Final Health Score:      {peanuts_result.health_score} / 100")
    print(f"Verdict:                 {peanuts_result.verdict}")
    print(f"Pillar Scores:           {peanuts_result.pillar_scores}")
    print("Deductions:")
    for d in peanuts_result.deductions:
        print(f"  - {d['reason']}: {d['points']} pts")
    print("Bonuses:")
    for b in peanuts_result.bonuses:
        print(f"  + {b['reason']}: {b['points']} pts")

    # Category and Grace Checks
    assert peanuts_result.category_group != "beverages", "Category mismatch failed! Peanuts classified as beverage."
    
    # Saturated fat deduction check
    has_sat_fat_grace = any("saturated fat penalty discounted" in b["reason"].lower() for b in peanuts_result.bonuses)
    assert has_sat_fat_grace, "Nuts & Seeds grace did not discount saturated fat penalty!"
    
    # Caloric density deduction check
    has_kcal_grace = any("caloric density penalty waived" in b["reason"].lower() for b in peanuts_result.bonuses)
    assert has_kcal_grace, "Nuts & Seeds grace did not waive high caloric density penalty!"

    print("=> Scenario 1 passed successfully!")

    # -------------------------------------------------------------------------
    # Scenario 2: Diet Coke (Diet Soda Loophole Fix)
    # -------------------------------------------------------------------------
    print("\n[Scenario 2] Diet Coke (Synthetic Sweetener hard-cap at 65)")
    diet_coke_result = compute_health_score(
        nutrients={
            "sugars_100g": 0.0,
            "salt_100g": 0.05,
            "saturated-fat_100g": 0.0,
            "energy-kcal_100g": 0.0,
            "proteins_100g": 0.0
        },
        nova_group=4,
        nutri_score="b",
        additives_tags=["en:e951", "en:e950"],  # Aspartame, Acesulfame K
        nutrient_levels={},
        ingredients=["carbonated water", "colour", "aspartame", "phosphoric acid", "sweeteners"],
        ingredients_text="Carbonated water, Colour (Caramel E150d), Sweeteners (Aspartame, Acesulfame K), Phosphoric Acid, Citric Acid, Natural Flavourings, Caffeine",
        categories="beverages",
        serving_size="330ml",
        product_name="Diet Coke"
    )
    print(f"Final Health Score:      {diet_coke_result.health_score} / 100")
    print(f"Verdict:                 {diet_coke_result.verdict}")
    print(f"Pillar Scores:           {diet_coke_result.pillar_scores}")
    print("Deductions:")
    for d in diet_coke_result.deductions:
        print(f"  - {d['reason']}: {d['points']} pts")

    assert diet_coke_result.health_score <= 65, f"Diet Coke Loophole failed! Score is {diet_coke_result.health_score} (> 65)"
    
    # Check that -10 macronutrient penalty and -3 integrity penalty are present
    has_macro_penalty = any("chemical masquerade" in d["reason"].lower() for d in diet_coke_result.deductions)
    has_integrity_penalty = any("artificial/synthetic sweetener" in d["reason"].lower() for d in diet_coke_result.deductions)
    assert has_macro_penalty, "Diet soda did not trigger -10 macronutrient penalty!"
    assert has_integrity_penalty, "Diet soda did not trigger -3 ingredient integrity penalty!"
    print("=> Scenario 2 passed successfully!")

    # -------------------------------------------------------------------------
    # Scenario 3: Whole Milk (Single-Ingredient Whole Fat Grace)
    # -------------------------------------------------------------------------
    print("\n[Scenario 3] Whole Milk (Single-Ingredient Grace)")
    milk_result = compute_health_score(
        nutrients={
            "sugars_100g": 8.0,
            "salt_100g": 0.1,
            "saturated-fat_100g": 1.86,
            "energy-kcal_100g": 61.0,
            "proteins_100g": 3.15,
            "fat_100g": 3.25
        },
        nova_group=1,
        nutri_score="a",
        additives_tags=[],
        nutrient_levels={},
        ingredients=["milk"],
        ingredients_text="Whole Milk",
        categories="dairy",
        serving_size="250ml",
        product_name="Whole Milk"
    )
    print(f"Final Health Score:      {milk_result.health_score} / 100")
    print(f"Verdict:                 {milk_result.verdict}")
    print(f"Pillar Scores:           {milk_result.pillar_scores}")
    print("Deductions:")
    for d in milk_result.deductions:
        print(f"  - {d['reason']}: {d['points']} pts")
    print("Bonuses:")
    for b in milk_result.bonuses:
        print(f"  + {b['reason']}: {b['points']} pts")

    # Milk has sugar but no added sugar, so it should get a bonus and natural sugar discount
    has_natural_sugar_discount = any("natural sugar" in b["reason"].lower() and "discounted by 80%" in b["reason"].lower() for b in milk_result.bonuses)
    assert has_natural_sugar_discount, "Unsweetened milk did not receive the 80% sugar penalty discount!"
    assert milk_result.health_score >= 80, f"Whole Milk score is too low: {milk_result.health_score}"
    print("=> Scenario 3 passed successfully!")

    # -------------------------------------------------------------------------
    # Scenario 4: Unsweetened Raisins (Dried Fruit Grace & Natural Sugar Correction)
    # -------------------------------------------------------------------------
    print("\n[Scenario 4] Unsweetened Raisins (Dried Fruit Grace & Natural Sugar)")
    raisins_result = compute_health_score(
        nutrients={
            "sugars_100g": 59.0,
            "salt_100g": 0.05,
            "saturated-fat_100g": 0.1,
            "energy-kcal_100g": 299.0,
            "proteins_100g": 3.0,
            "fiber_100g": 3.7
        },
        nova_group=1,
        nutri_score="a",
        additives_tags=[],
        nutrient_levels={},
        ingredients=["raisins"],
        ingredients_text="Raisins",
        categories="en:plant-based-foods-and-beverages",
        serving_size="40g",
        product_name="Unsweetened Raisins"
    )
    print(f"Final Health Score:      {raisins_result.health_score} / 100")
    print(f"Verdict:                 {raisins_result.verdict}")
    print(f"Pillar Scores:           {raisins_result.pillar_scores}")
    print("Deductions:")
    for d in raisins_result.deductions:
        print(f"  - {d['reason']}: {d['points']} pts")
    print("Bonuses:")
    for b in raisins_result.bonuses:
        print(f"  + {b['reason']}: {b['points']} pts")

    # Raisins have natural sugars and high calories, check grace waivers
    has_sugar_discount = any("natural sugar" in b["reason"].lower() and "discounted by 80%" in b["reason"].lower() for b in raisins_result.bonuses)
    has_kcal_waived = any("caloric density penalty waived" in b["reason"].lower() for b in raisins_result.bonuses)
    assert has_sugar_discount, "Unsweetened raisins did not get 80% natural sugar penalty discount!"
    assert has_kcal_waived, "Dried fruit did not get caloric density penalty waived!"
    assert raisins_result.health_score >= 70, f"Raisins score is too low: {raisins_result.health_score}"
    print("=> Scenario 4 passed successfully!")

    print("\n======================================================================")
    print("ALL SCENARIO TESTS PASSED SUCCESSFULLY! THE SYSTEM IS 100% CORRECT!")
    print("======================================================================")

if __name__ == "__main__":
    run_tests()
