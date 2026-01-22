import asyncio
from app.services.openfoodfacts import get_product_from_api
from app.models.products import Product
from datetime import datetime

async def main():
    barcode = "3017620422003" # Nutella
    print(f"Fetching data for barcode: {barcode}")
    try:
        data = await get_product_from_api(barcode)
        if not data:
            print("Failed to fetch data")
            return
        
        print("\n--- Fetched Data Keys ---")
        for k, v in data.items():
            print(f"{k}: {type(v)}")

        print("\n--- Checking New Fields ---")
        new_fields = ['countries', 'allergens', 'additives_tags', 'serving_size', 'ecoscore_grade', 'nutrient_levels', 'packaging', 'categories']
        for field in new_fields:
            val = data.get(field)
            if isinstance(val, str) and len(val) > 50:
                val = val[:50] + "..."
            print(f"{field}: {val}")

        print("\n--- Validating Product Model ---")
        try:
            # mimic routes.py instantiation
            p_data = {
                "barcode": barcode,
                "name": data["name"],
                "brand": data["brand"],
                "image_url": data.get("image_url"),
                "quantity": data.get("quantity"),
                "ingredients_text": data.get("ingredients_text"),
                "nutrients": data.get("nutrients"),
                "nova_group": data.get("nova_group"),
                "nova_tags": data.get("nova_tags", []),
                "ingredients": data.get("ingredients_tags", []),
                "nutri_score": data.get("nutri_score"),
                "categories": data.get("categories"),
                "countries": data.get("countries"),
                "allergens": data.get("allergens"),
                "additives_tags": data.get("additives_tags", []),
                "serving_size": data.get("serving_size"),
                "ecoscore_grade": data.get("ecoscore_grade"),
                "nutrient_levels": data.get("nutrient_levels"),
                "packaging": data.get("packaging"),
                "verdict": "Test Verdict",
                "roast_or_toast": "Toast",
                "reasoning": "Test Reasoning"
            }
            
            # Verify Product Model (DB)
            p = Product(**p_data)
            print("Product model instantiated successfully!")
            
            # Verify ProductResponse (API)
            from app.models.schemas import ProductResponse
            # ProductResponse expects the object attributes relative to the Pydantic model
            # But since Product is SQLModel (and thus Pydantic), we can pass it to from_orm or just dump it.
            # However, ProductResponse has computed fields. We should try standard validation.
            p_res = ProductResponse.model_validate(p)
            print("ProductResponse validated successfully!")
            print(f"Verdict Color: {p_res.verdict_color}")
            
        except Exception as e:
            print(f"Model validation failed: {e}")
            import traceback
            traceback.print_exc()

    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    asyncio.run(main())
