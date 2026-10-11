"""
"Buy this instead": healthier products from the same OFF category that are sold in India.

The search index only carries Nutri-Score, so it is used to shortlist; each shortlisted product is
then fetched in full and re-scored with our own engine (Nutri-Score alone rates some Indian
biscuits "A", and OFF data is often incomplete).
"""
import asyncio
import logging
from typing import Optional

from app.services.openfoodfacts import OFFUnavailable, get_product_from_api, search_category_candidates
from app.services.personal import personal_alerts
from app.services.scoring import compute_health_score, _split_tags, _UNINFORMATIVE_TAGS

logger = logging.getLogger(__name__)

ALTERNATIVES_VERSION = 2
MIN_IMPROVEMENT = 10   # points better than the scanned product
MIN_SCORE = 50         # never suggest something we would rate LIMIT or AVOID
SHORTLIST = 8          # products fetched in full per scan (OFF allows ~100 product reads/min)
MAX_RESULTS = 5


def _search_tags(categories: Optional[str]) -> list:
    """Most specific first, at most two levels, never the uninformative top-level tags."""
    raw = [t.strip() for t in (categories or "").split(",") if t.strip()]
    readable = _split_tags(raw)
    usable = [tag for tag, name in zip(raw, readable) if name not in _UNINFORMATIVE_TAGS]
    if len(usable) > 1:
        usable = usable[1:]  # the broadest remaining tag ("snacks") is too wide to compare against
    return list(reversed(usable))[:2]


def score_off_product(raw: dict):
    return compute_health_score(
        nutrients=raw.get("nutrients") or {}, nova_group=raw.get("nova_group"),
        nutri_score=raw.get("nutri_score"), additives_tags=raw.get("additives_tags") or [],
        nutrient_levels=raw.get("nutrient_levels") or {},
        ingredients=raw.get("ingredients_tags") or ["Unknown Ingredients"],
        ingredients_text=raw.get("ingredients_text") or "", categories=raw.get("categories"),
        serving_size=raw.get("serving_size"), product_name=raw.get("name"),
        food_groups=raw.get("food_groups") or [], off_allergens=raw.get("allergens"),
    )


def _num(n: dict, key: str) -> Optional[float]:
    v = n.get(key)
    try:
        return float(v) if v is not None else None
    except (TypeError, ValueError):
        return None


def _why_better(current: dict, alt: dict, current_nova: Optional[int], alt_nova: Optional[int]) -> list:
    reasons = []
    for key, word in (("sugars_100g", "Less sugar"), ("salt_100g", "Less salt"),
                      ("saturated-fat_100g", "Less saturated fat")):
        a, b = _num(current, key), _num(alt, key)
        if a is not None and b is not None and a > 0.5 and b <= a * 0.7:
            reasons.append(word)
    for key, word in (("proteins_100g", "More protein"), ("fiber_100g", "More fibre")):
        a, b = _num(current, key), _num(alt, key)
        if a is not None and b is not None and b >= max(a * 1.3, a + 2):
            reasons.append(word)
    if current_nova == 4 and alt_nova in (1, 2, 3):
        reasons.append("Less processed")
    return reasons[:2]


async def find_alternatives(product, current_score: int) -> list:
    """Returns cached-shape dicts; per-user filtering happens in filter_for_user()."""
    codes, seen = [], {product.barcode}
    for tag in _search_tags(product.categories):
        for c in await search_category_candidates(tag, exclude_code=product.barcode):
            if c["code"] not in seen:
                seen.add(c["code"])
                codes.append(c["code"])
        if len(codes) >= SHORTLIST:
            break
    codes = codes[:SHORTLIST]
    if not codes:
        return []

    gate = asyncio.Semaphore(4)  # OFF throttles bursts

    async def fetch(code):
        async with gate:
            try:
                return await get_product_from_api(code, retries=0)
            except OFFUnavailable:
                return None

    raws = await asyncio.gather(*(fetch(code) for code in codes))
    results = []
    for code, raw in zip(codes, raws):
        if not raw:
            continue
        nutrients = raw.get("nutrients") or {}
        if not nutrients.get("energy-kcal_100g") and not nutrients.get("energy_100g"):
            continue  # too little data to judge
        try:
            r = score_off_product(raw)
        except Exception as e:
            logger.warning(f"Scoring alternative {code} failed: {e}")
            continue
        if r.data_confidence < 0.6 or r.health_score < max(MIN_SCORE, current_score + MIN_IMPROVEMENT):
            continue
        results.append({
            "code": code,
            "name": raw.get("name"),
            "brand": raw.get("brand"),
            "image_url": raw.get("image_small_url"),
            "nutri_score": raw.get("nutri_score"),
            "health_score": r.health_score,
            "verdict": r.verdict,
            "why_better": _why_better(product.nutrients or {}, nutrients, product.nova_group, raw.get("nova_group")),
            "allergens": r.allergens,
            "diet_tags": raw.get("diet_tags") or [],
            "ingredients_text": (raw.get("ingredients_text") or "")[:1500],
        })
    results.sort(key=lambda a: -a["health_score"])
    return results


def filter_for_user(alternatives: list, profile_ctx: Optional[dict]) -> list:
    """Drops suggestions this user can't eat, and strips internal fields from the response."""
    public = []
    for a in alternatives or []:
        if personal_alerts(a.get("allergens", []), a.get("diet_tags"), a.get("ingredients_text"), profile_ctx):
            continue
        public.append({k: a.get(k) for k in
                       ("code", "name", "brand", "image_url", "nutri_score", "health_score", "verdict", "why_better")})
        if len(public) >= MAX_RESULTS:
            break
    return public
