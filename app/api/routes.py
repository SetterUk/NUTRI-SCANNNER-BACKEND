from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_session
from app.models.products import Product, UserAddedProduct
from app.services.openfoodfacts import get_product_from_api
from app.models.schemas import ProductResponse, ReportMissingRequest, ManualProductRequest, UserRegister, UserLogin
from app.api.deps import create_access_token, get_current_user, get_password_hash, verify_password
from app.models.users import User, UserProfile
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests
from app.core.config import settings
from pydantic import BaseModel

# --- 1. IMPORT THE LANGGRAPH WORKFLOW ---
from app.agents.workflow import nutrition_app_workflow

router = APIRouter()

class TokenData(BaseModel):
    id_token: str

@router.post("/auth/register")
async def register_user(data: UserRegister, session: AsyncSession = Depends(get_session)):
    """Email/Password Registration"""
    # 1. Check if email exists
    statement = select(User).where(User.email == data.email)
    result = await session.execute(statement)
    if result.scalars().first():
        raise HTTPException(status_code=400, detail="Email already registered")

    # 2. Create User
    new_user = User(
        email=data.email,
        hashed_password=get_password_hash(data.password),
        full_name=data.full_name
    )
    session.add(new_user)
    await session.flush()

    # 3. Create Profile
    profile = UserProfile(user_id=new_user.id)
    session.add(profile)
    await session.commit()
    await session.refresh(new_user)

    # 4. Generate Token
    access_token = create_access_token(data={"sub": str(new_user.id)})
    return {"access_token": access_token, "token_type": "bearer", "user_id": new_user.id}

@router.post("/auth/login")
async def login_user(data: UserLogin, session: AsyncSession = Depends(get_session)):
    """Email/Password Login"""
    statement = select(User).where(User.email == data.email)
    result = await session.execute(statement)
    user = result.scalars().first()

    if not user or not user.hashed_password:
        raise HTTPException(status_code=401, detail="Invalid email or password")
    
    if not verify_password(data.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    access_token = create_access_token(data={"sub": str(user.id)})
    return {"access_token": access_token, "token_type": "bearer", "user_id": user.id}

@router.post("/auth/google")
async def google_auth(data: TokenData, session: AsyncSession = Depends(get_session)):
    """Verifies Google ID Token and returns custom JWT."""
    try:
        # 1. Verify Google Token
        id_info = id_token.verify_oauth2_token(
            data.id_token, 
            google_requests.Request(),
            audience=settings.GOOGLE_CLIENT_ID
        )

        google_id = id_info['sub']
        email = id_info['email']
        name = id_info.get('name', "User")

        # 2. Check if user exists
        statement = select(User).where(User.google_id == google_id)
        result = await session.execute(statement)
        user = result.scalars().first()

        if not user:
            # Create new user
            user = User(
                google_id=google_id,
                email=email,
                full_name=name
            )
            session.add(user)
            await session.flush() # Get user.id

            # Create default profile
            profile = UserProfile(user_id=user.id)
            session.add(profile)
            await session.commit()
            await session.refresh(user)
        
        # 3. Create backend JWT
        access_token = create_access_token(data={"sub": str(user.id)})

        return {
            "access_token": access_token,
            "token_type": "bearer",
            "user": {
                "id": user.id,
                "email": user.email,
                "full_name": user.full_name
            }
        }
    except Exception as e:
        raise HTTPException(status_code=401, detail=f"Invalid Google token: {str(e)}")

@router.post("/scan/{barcode}", response_model=ProductResponse)
async def scan_product(
    barcode: str,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    # Fetch user profile for personalization
    profile_stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
    profile_result = await session.execute(profile_stmt)
    profile = profile_result.scalars().first()

    # 1. CHECK CACHE FOR BASE PRODUCT DATA
    statement = select(Product).where(Product.barcode == barcode)
    result = await session.execute(statement)
    product = result.scalars().first()

    if not product:
        # 2. FETCH EXTERNAL DATA IF NOT IN CACHE
        raw_data = await get_product_from_api(barcode)
        if not raw_data:
            raise HTTPException(status_code=404, detail="Product not found in global database.")

        ingredients_list = raw_data.get("ingredients_tags", [])
        if not ingredients_list:
            ingredients_list = ["Unknown Ingredients"]

        product = Product(
            barcode=barcode,
            name=raw_data.get("name", "Unknown"),
            brand=raw_data.get("brand"),
            image_url=raw_data.get("image_url"),
            quantity=raw_data.get("quantity", ""),
            ingredients_text=raw_data.get("ingredients_text", ""),
            nutrients=raw_data.get("nutrients", {}), 
            nova_group=raw_data.get("nova_group"),
            nova_tags=raw_data.get("nova_tags") or [],
            source="OFF",
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
        )
        session.add(product)
    else:
        product.scan_count += 1
        session.add(product)

    # Use actual product data (either from cache or fresh OFF) for analysis
    # This ensures consistency
    
    # --- 3. RUN PERSONALIZED AGENTIC WORKFLOW ---
    initial_state = {
        "barcode": barcode,
        "product_name": product.name,
        "ingredients": product.ingredients,
        "ingredients_text": product.ingredients_text or "",
        "category_tag": product.categories or "",
        "is_food": True,
        "analysis_result": None,
        "final_response": {},
        # Scoring engine inputs
        "nova_group": product.nova_group,
        "nutri_score": product.nutri_score,
        "additives_tags": product.additives_tags or [],
        "nutrient_levels": product.nutrient_levels or {},
        "serving_size": product.serving_size,
        "nutrients": product.nutrients or {},
        "scoring_result": None,
        # PERSONALIZATION CONTEXT
        "user_profile": {
            "dietary_preferences": profile.dietary_preferences if profile else None,
            "health_tags": profile.health_tags if profile else [],
            "allergies": profile.allergies if profile else [],
            "health_goals": profile.health_goals if profile else None
        }
    }

    try:
        final_state = await nutrition_app_workflow.ainvoke(initial_state)
        agent_output = final_state.get("final_response", {})
    except Exception as e:
        print(f"⚠️ Agent Workflow Failed: {e}")
        agent_output = {
            "verdict": "PASS",
            "health_score": 0,
            "summary": "Analysis failed.",
            "ingredients_analysis": []
        }

    await session.commit()
    await session.refresh(product)

    # Build response: Product Data + Personalized Analysis
    response_data = product.model_dump()
    response_data.update(agent_output)
    
    return response_data

@router.post("/report-missing")
async def report_missing_product(
    data: ReportMissingRequest,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """
    Log a product that was not found in the external database.
    Saves ONLY to UserAddedProduct table.
    """
    new_log = UserAddedProduct(
        name=data.name,
        quantity=data.quantity,
        barcode=data.barcode
    )
    session.add(new_log)
    await session.commit()
    return {"status": "success", "message": "Product reported successfully"}
