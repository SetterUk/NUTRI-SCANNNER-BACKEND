from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_session
from app.models.products import Product
from app.services.openfoodfacts import get_product_from_api
from app.agents.nutritionist import analyze_product

router = APIRouter()

@router.post("/scan/{barcode}")
async def scan_product(
    barcode: str,
    user_profile: str = "General Health",
    session: AsyncSession = Depends(get_session)
):
    statement = select(Product).where(Product.barcode == barcode)
    result = await session.execute(statement)
    cached_product = result.scalars().first()

    if cached_product:
        cached_product.scan_count += 1
        session.add(cached_product)
        await session.commit()
        await session.refresh(cached_product)
        return cached_product

    raw_data = await get_product_from_api(barcode)

    if not raw_data:
        raise HTTPException(status_code=404, detail="Product not found in global database.")

    ingredients_list = raw_data.get("ingredients_tags", [])
    if not ingredients_list:
        ingredients_list = ["Unknown Ingredients"]

    ai_result = await analyze_product(
        product_name=raw_data["name"],
        ingredients=ingredients_list,
        user_profile=user_profile
    )

    new_product = Product(
        barcode=barcode,
        name=raw_data["name"],
        brand=raw_data["brand"],
        image_url=raw_data["image_url"],
        quantity=raw_data.get("quantity"),
        ingredients=ingredients_list,
        ingredients_text=raw_data.get("ingredients_text", ""),
        nutrients=raw_data.get("nutrients", {}),
        nova_group=raw_data.get("nova_group"),
        nova_tags=raw_data.get("nova_tags", []),
        nutri_score=raw_data["nutri_score"],
        Category_tag=raw_data["category_tag"],
        verdict=ai_result.get("verdict", "PASS"),
        roast_or_toast=ai_result.get("roast_or_toast", "AI Analysis Failed"),
        reasoning=ai_result.get("reasoning", "Check ingredients manually.")
    )

    session.add(new_product)
    await session.commit()
    await session.refresh(new_product)

    return new_product