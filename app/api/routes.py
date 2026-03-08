from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_session
from app.models.products import Product
from app.services.openfoodfacts import get_product_from_api
from app.models.schemas import ProductResponse

# --- 1. IMPORT THE LANGGRAPH WORKFLOW ---
from app.agents.workflow import nutrition_app_workflow

router = APIRouter()

@router.post("/scan/{barcode}", response_model=ProductResponse)
async def scan_product(
    barcode: str,
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

    # 2. FETCH EXTERNAL DATA
    raw_data = await get_product_from_api(barcode)

    if not raw_data:
        raise HTTPException(status_code=404, detail="Product not found in global database.")

    ingredients_list = raw_data.get("ingredients_tags", [])
    if not ingredients_list:
        ingredients_list = ["Unknown Ingredients"]

    # --- 3. RUN THE AGENTIC WORKFLOW ---
    initial_state = {
        "barcode": barcode,
        "product_name": raw_data.get("name", "Unknown"),
        "ingredients": ingredients_list,
        "ingredients_text": raw_data.get("ingredients_text", ""),
        "category_tag": raw_data.get("categories", ""), 
        "is_food": True,
        "analysis_result": None,
        "final_response": {}
    }

    try:
        final_state = await nutrition_app_workflow.ainvoke(initial_state)
        agent_output = final_state.get("final_response", {})
    except Exception as e:
        print(f"⚠️ Agent Workflow Failed: {e}")
        agent_output = {
            "verdict": "PASS",
            "health_score": 0,
            "summary": "Our AI factory is on a coffee break. Better safe than sorry.",
            "ingredients_analysis": []
        }

    # --- 4. SAVE TO DB ---
    new_product = Product(
        barcode=barcode,
        name=raw_data.get("name", "Unknown"),
        brand=raw_data.get("brand"),
        image_url=raw_data.get("image_url"),
        quantity=raw_data.get("quantity", ""),
        ingredients_text=raw_data.get("ingredients_text", ""),
        nutrients=raw_data.get("nutrients", {}), 
        nova_group=raw_data.get("nova_group"),
        nova_tags=raw_data.get("nova_tags") or [],
        
        ingredients=ingredients_list,
        nutri_score=raw_data.get("nutri_score"),
        categories=raw_data.get("categories"), 
        countries=raw_data.get("countries"),
        allergens=raw_data.get("allergens"),
        additives_tags=raw_data.get("additives_tags") or [],
        serving_size=raw_data.get("serving_size"),
        ecoscore_grade=raw_data.get("ecoscore_grade"),
        nutrient_levels=raw_data.get("nutrient_levels", {}),
        packaging=raw_data.get("packaging"),
        
        verdict=agent_output.get("verdict", "PASS"),
        is_good_for_health=agent_output.get("is_good_for_health", False),
        health_reason=agent_output.get("health_reason", "Analysis failed."),
        health_scale=agent_output.get("health_scale", 1.0),
        safe_consumption_frequency=agent_output.get("safe_consumption_frequency", "Unknown"),
        health_score=agent_output.get("health_score", 0),
        summary=agent_output.get("summary", "Analysis failed."),
        ingredients_analysis=agent_output.get("ingredients_analysis", []),
        nutrition_analysis=agent_output.get("nutrition_analysis", {})
    )

    session.add(new_product)
    await session.commit()
    await session.refresh(new_product)

    return new_product
