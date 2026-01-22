from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_session
from app.models.products import Product
from app.services.openfoodfacts import get_product_from_api
from app.agents.nutritionist import analyze_product

from app.models.schemas import ProductResponse

router = APIRouter()

@router.post("/scan/{barcode}", response_model=ProductResponse)
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
        quantity=raw_data.get("quantity", ""),
        ingredients_text=raw_data.get("ingredients_text", ""),
        nutrients=raw_data.get("nutrients", {}), 
        nova_group=raw_data.get("nova_group"),
        nova_tags=raw_data.get("nova_tags", []),
        
        ingredients=ingredients_list,
        nutri_score=raw_data.get("nutri_score"),
        categories=raw_data.get("categories"), 
        countries=raw_data.get("countries"),
        allergens=raw_data.get("allergens"),
        additives_tags=raw_data.get("additives_tags", []),
        serving_size=raw_data.get("serving_size"),
        ecoscore_grade=raw_data.get("ecoscore_grade"),
        nutrient_levels=raw_data.get("nutrient_levels", {}),
        packaging=raw_data.get("packaging"),
        
        verdict=ai_result.verdict,
        roast_or_toast=ai_result.roast_or_toast,
        reasoning=ai_result.reasoning
    )

    session.add(new_product)
    await session.commit()
    await session.refresh(new_product)

    return new_product