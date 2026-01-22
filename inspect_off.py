import httpx
import asyncio
import json

async def main():
    url = "https://world.openfoodfacts.org/api/v2/product/3017620422003.json"
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.get(url)
            print(f"Status Code: {resp.status_code}")
            data = resp.json()
            product = data.get('product', {})
            
            print("\n--- Top Level Keys (First 20) ---")
            print(list(product.keys())[:20])
            
            print("\n--- Interesting Fields ---")
            interesting = [
                'code', 'product_name', 'brands', 'categories', 'countries', 
                'ecoscore_grade', 'nutrient_levels', 'additives_tags', 
                'allergens', 'serving_size', 'packaging', 'image_url', 
                'nutriscore_grade', 'nova_group', 'nutriments'
            ]
            for k in interesting:
                val = product.get(k)
                # Truncate long values
                if isinstance(val, str) and len(val) > 100:
                    val = val[:100] + "..."
                if isinstance(val, list) and len(val) > 5:
                    val = val[:5] + ["..."]
                print(f"{k}: {val}")
                
            print("\n--- Nutriments Keys ---")
            nutriments = product.get('nutriments', {})
            print(list(nutriments.keys())[:20])

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
