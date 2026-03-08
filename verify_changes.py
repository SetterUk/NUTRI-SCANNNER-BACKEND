import asyncio
from app.agents.nutritionist import analyze_product_detailed

async def main():
    result = await analyze_product_detailed(
        product_name="Sample Drink",
        ingredients_text="water, lychee, sugar, acid, antioxidant, stabiliser, flavouring, e330, vitamin-c, xanthan",
        ingredients_list=[
            "en:water", "en:lychee", "en:sugar", "en:acid", "en:antioxidant",
            "en:stabiliser", "en:flavouring", "en:e330", "en:vitamin-c", "fr:xanthan"
        ],
        nutrients={
            "carbohydrates": 66.4, "carbohydrates_100g": 66.4, "carbohydrates_unit": "g",
            "energy-kcal_100g": 316, "fat_100g": 1.1, "fiber_100g": 1,
            "proteins_100g": 9, "salt_100g": 16.4, "saturated-fat_100g": 0.4,
            "sodium_100g": 6.56, "sugars_100g": 15.9
        }
    )
    print("\n--- TEST RESULT ---")
    print(result.model_dump_json(indent=2))

if __name__ == "__main__":
    asyncio.run(main())
