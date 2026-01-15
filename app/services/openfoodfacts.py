import httpx
from typing import Optional, Dict, Any

async def get_product_from_api(barcode: str) -> Optional[Dict[str, Any]]:
    url = f"https://world.openfoodfacts.org/api/v2/product/{barcode}.json"

    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(url, timeout=10.0)
            data = response.json()

            if data.get("status") != 1:
                return None
            product = data['product']

            return {
                "name": product.get("product_name", "Unknown Product"),
                "brand": product.get("brands", "Unknown Brand"),
                "image_url": product.get("image_url", None),
                "ingredients_text": product.get("ingredients_text", ""),
                "ingredients_tags": product.get("ingredients_original_tags", []),
                "nutri_score": product.get("nutriscore_grade", "unknown"),
                "category_tag": product.get("categories_tags", ["unknown"])[0] if product.get("categories_tags") else None
            }
        except Exception as e:
            print(f"Error fetching from OFF: {e}")
            return None