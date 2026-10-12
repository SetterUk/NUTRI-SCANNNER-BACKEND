import asyncio
import logging
import httpx
from typing import Optional, Dict, Any, List

logger = logging.getLogger(__name__)

# OFF asks every app to identify itself; anonymous clients get throttled first.
HEADERS = {"User-Agent": "HealthHeat/1.0 (Android nutrition app; https://nutri-scanner.onrender.com)"}

PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product/{barcode}.json"
# The classic /cgi/search.pl and /api/v2/search endpoints are frequently unavailable;
# search-a-licious is OFF's supported search service.
SEARCH_URL = "https://search.openfoodfacts.org/search"

# The search index has no ingredient text or additives, so it is only used to shortlist;
# the shortlisted products are then fetched in full with get_product_from_api().
_SEARCH_FIELDS = "code,product_name,brands,nutriscore_grade,nutriscore_score"


def _diet_tags(product: Dict[str, Any]) -> List[str]:
    """OFF's vegan/vegetarian analysis, e.g. ['en:non-vegan', 'en:vegetarian']."""
    return [t for t in product.get("ingredients_analysis_tags") or []
            if "vegan" in t or "vegetarian" in t]


class OFFUnavailable(Exception):
    """OFF is down or throttling us. Distinct from "product not found"."""


async def get_product_from_api(barcode: str, retries: int = 1) -> Optional[Dict[str, Any]]:
    """Returns None when OFF doesn't know the barcode; raises OFFUnavailable when OFF can't answer."""
    url = PRODUCT_URL.format(barcode=barcode)
    last_error = None
    for attempt in range(retries + 1):
        try:
            async with httpx.AsyncClient(headers=HEADERS) as client:
                response = await client.get(url, timeout=10.0)
            if response.status_code >= 500 or response.status_code == 429:
                raise OFFUnavailable(f"HTTP {response.status_code}")
            try:
                data = response.json()
            except ValueError:
                # OFF serves an HTML "temporarily unavailable" page when overloaded
                raise OFFUnavailable("non-JSON response")
            if data.get("status") != 1:
                return None
            return _product_fields(data["product"])
        except (OFFUnavailable, httpx.HTTPError) as e:
            last_error = e
            if attempt < retries:
                await asyncio.sleep(0.8 * (attempt + 1))
    logger.warning(f"OFF unavailable for {barcode}: {last_error}")
    raise OFFUnavailable(str(last_error))


def _product_fields(product: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "name": product.get("product_name", "Unknown Product"),
        "brand": product.get("brands", "Unknown Brand"),
        "image_url": product.get("image_url", None),
        "image_small_url": product.get("image_front_small_url") or product.get("image_url"),
        "quantity": product.get("quantity", ""),
        "ingredients_text": product.get("ingredients_text", ""),
        "ingredients_tags": product.get("ingredients_original_tags", []),
        "nutri_score": product.get("nutriscore_grade", "unknown"),
        "nova_group": product.get("nova_group"),
        "nova_tags": product.get("nova_groups_tags", []),
        # Full list, broad → specific. Keeping only the first (broadest) tag put most
        # Indian snacks in the wrong scoring group.
        "categories": ",".join(product.get("categories_tags") or []) or None,
        "food_groups": product.get("food_groups_tags") or [],
        "diet_tags": _diet_tags(product),
        "nutrients": product.get("nutriments", {}),
        "countries": product.get("countries", ""),
        "allergens": ",".join(product.get("allergens_tags") or []) or product.get("allergens", ""),
        "additives_tags": product.get("additives_tags", []),
        "serving_size": product.get("serving_size", ""),
        "ecoscore_grade": product.get("ecoscore_grade", ""),
        "nutrient_levels": product.get("nutrient_levels", {}),
        "packaging": product.get("packaging", "")
    }


async def search_category_candidates(category_tag: str, exclude_code: str,
                                     page_size: int = 30) -> List[Dict[str, Any]]:
    """Barcodes of products sold in India in the same OFF category, best Nutri-Score first."""
    params = {
        "q": f'categories_tags:"{category_tag}" AND countries_tags:"en:india"',
        "page_size": page_size,
        "fields": _SEARCH_FIELDS,
        "sort_by": "nutriscore_score",
    }
    try:
        async with httpx.AsyncClient(headers=HEADERS) as client:
            response = await client.get(SEARCH_URL, params=params, timeout=8.0)
            response.raise_for_status()
            hits = response.json().get("hits") or []
    except Exception as e:
        logger.warning(f"OFF alternatives search failed for {category_tag}: {e}")
        return []

    return [
        {"code": h["code"], "nutri_score": h.get("nutriscore_grade")}
        for h in hits
        if h.get("code") and h["code"] != exclude_code and h.get("product_name")
    ]
