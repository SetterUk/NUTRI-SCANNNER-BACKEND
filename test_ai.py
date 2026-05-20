import asyncio
from app.agents.nutritionist import analyze_product_detailed
from app.services.scoring import compute_health_score

async def test_ai():
    print("Testing AI...")
    try:
        product_name = "Nutella"
        ingredients_text = "Sugar, palm oil, hazelnuts (13%), skimmed milk powder (8.7%), fat-reduced cocoa (7.4%), emulsifier: lecithin (soya), vanillin."
        ingredients_list = [
            "en:sugar", "en:palm-oil", "en:hazelnut", "en:skimmed-milk-powder",
            "en:fat-reduced-cocoa", "en:emulsifier", "en:lecithin", "en:soya", "en:vanillin"
        ]
        nutrients = {
            "sugars_100g": 56.3,
            "fat_100g": 30.9,
            "saturated-fat_100g": 10.6,
            "energy-kcal_100g": 539,
            "proteins_100g": 6.3,
            "salt_100g": 0.107
        }
        
        scoring = compute_health_score(
            nutrients=nutrients,
            nova_group=4,
            nutri_score="e",
            additives_tags=[],
            nutrient_levels={},
            ingredients=ingredients_list,
            ingredients_text=ingredients_text,
            categories="confectionery",
            serving_size=None,
            product_name=product_name
        )

        result = await analyze_product_detailed(
            product_name=product_name,
            ingredients_text=ingredients_text,
            ingredients_list=ingredients_list,
            nutrients=nutrients,
            scoring=scoring
        )
        print("Success! Result:")
        print(result.model_dump_json(indent=2))
    except Exception as e:
        print(f"FAILED WITH EXCEPTION: {e}")

if __name__ == "__main__":
    asyncio.run(test_ai())
