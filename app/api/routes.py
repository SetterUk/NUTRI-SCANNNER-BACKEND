from typing import Optional
from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_session
from app.models.products import Product, UserAddedProduct
from app.services.openfoodfacts import get_product_from_api
from app.models.schemas import ProductResponse, ReportMissingRequest, ManualProductRequest, UserRegister, UserLogin, ContributeProductRequest
from app.api.deps import create_access_token, get_current_user, get_password_hash, verify_password, get_current_user_optional
from app.models.users import User, UserProfile, UserScan
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests
from app.core.config import settings
from pydantic import BaseModel
from sqlalchemy.orm import selectinload
from typing import List

# --- 1. IMPORT THE LANGGRAPH WORKFLOW ---
from app.agents.workflow import nutrition_app_workflow
import logging

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

router = APIRouter()

@router.get("/ping")
async def ping():
    return {"ping": "pong"}

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
    print(f"DEBUG: Received scan request for {barcode}")
    # Fetch user profile for personalization if user is authenticated
    profile = None
    if current_user:
        profile_stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
        profile_result = await session.execute(profile_stmt)
        profile = profile_result.scalars().first()

    # 1. CHECK CACHE FOR BASE PRODUCT DATA
    logger.info(f"Querying DB for barcode {barcode}")
    statement = select(Product).where(Product.barcode == barcode)
    result = await session.execute(statement)
    product = result.scalars().first()
    logger.info(f"DB query finished for barcode {barcode}")

    if not product:
        # 2. FETCH EXTERNAL DATA IF NOT IN CACHE
        logger.info(f"Fetching from OFF API for {barcode}")
        raw_data = await get_product_from_api(barcode)
        logger.info(f"OFF API response received for {barcode}")
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

    # 3. USE CACHED AI ANALYSIS IF AVAILABLE
    if product.health_score is not None:
        logger.info(f"Serving cached AI analysis for barcode {barcode}")
        await session.commit()
        await session.refresh(product)
        return product.model_dump()

    # --- 4. RUN PERSONALIZED AGENTIC WORKFLOW ---
    logger.info(f"Running agent workflow for barcode {barcode}")
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
            "age": profile.age if profile else None,
            "weight_kg": profile.weight_kg if profile else None,
            "height": profile.height if profile else None,
            "dietary_preferences": profile.dietary_preferences if profile else None,
            "health_tags": profile.health_tags if profile else [],
            "allergies": profile.allergies if profile else [],
            "health_goals": profile.health_goals if profile else None
        }
    }

    try:
        logger.info("Calling nutrition_app_workflow")
        final_state = await nutrition_app_workflow.ainvoke(initial_state)
        logger.info("Workflow completed")
        agent_output = final_state.get("final_response", {})
        
        # Save AI output to product cache
        product.verdict = agent_output.get("verdict")
        product.is_good_for_health = agent_output.get("is_good_for_health")
        product.health_reason = agent_output.get("health_reason")
        product.health_scale = agent_output.get("health_scale")
        product.safe_consumption_frequency = agent_output.get("safe_consumption_frequency")
        product.health_score = agent_output.get("health_score")
        product.summary = agent_output.get("summary")
        product.ingredients_analysis = agent_output.get("ingredients_analysis", [])
        product.nutrition_analysis = agent_output.get("nutrition_analysis", {})
        
    except Exception as e:
        logger.error(f"Agent Workflow Failed for {barcode}: {e}", exc_info=True)
        agent_output = {
            "verdict": "PASS",
            "health_score": 0,
            "summary": "Analysis failed.",
            "ingredients_analysis": []
        }

    await session.commit()
    await session.refresh(product)

    # 5. LOG THE SCAN FOR THE USER
    if current_user:
        # Check if they already scanned this recently (optional, but let's just log it)
        user_scan = UserScan(user_id=current_user.id, barcode=barcode)
        session.add(user_scan)
        await session.commit()

    # Build response: Product Data + Personalized Analysis
    response_data = product.model_dump()
    response_data.update(agent_output)
    
    return response_data

@router.get("/scan/history", response_model=List[ProductResponse])
async def get_scan_history(
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """
    Get the user's scan history, ordered by most recent first.
    """
    if not current_user:
        raise HTTPException(status_code=401, detail="Not authenticated")

    # Fetch the latest 50 distinct scans for the user
    # Note: A real app might group by barcode or distinct, but this is simple history
    statement = (
        select(Product)
        .join(UserScan, UserScan.barcode == Product.barcode)
        .where(UserScan.user_id == current_user.id)
        .order_by(UserScan.scanned_at.desc())
        .limit(50)
    )
    result = await session.execute(statement)
    products = result.scalars().all()
    
    # Remove duplicates while preserving order
    seen = set()
    unique_products = []
    for p in products:
        if p.barcode not in seen:
            seen.add(p.barcode)
            unique_products.append(p)

    return unique_products

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

@router.patch("/scan/{barcode}/contribute")
async def contribute_to_product(
    barcode: str,
    data: ContributeProductRequest,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """
    Allows a user to contribute missing name and ingredients.
    This wipes the old AI cache so it can be re-analyzed next time.
    """
    statement = select(Product).where(Product.barcode == barcode)
    result = await session.execute(statement)
    product = result.scalars().first()

    if not product:
        # If it truly doesn't exist, create a skeleton product
        product = Product(
            barcode=barcode,
            name=data.name,
            ingredients_text=data.ingredients_text,
            ingredients=[i.strip() for i in data.ingredients_text.split(",")],
            source="MANUAL"
        )
        session.add(product)
    else:
        # Update existing
        product.name = data.name
        product.ingredients_text = data.ingredients_text
        product.ingredients = [i.strip() for i in data.ingredients_text.split(",")]
        
        # Invalidate AI Cache!
        product.health_score = None
        product.verdict = None
        product.summary = None
        product.ingredients_analysis = []
        product.nutrition_analysis = {}
        session.add(product)

    await session.commit()
    return {"status": "success", "message": "Product contributed successfully"}

@router.get("/history", response_model=List[ProductResponse])
async def get_user_history(
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """
    Returns the list of products previously scanned by the user, ordered by most recent.
    """
    statement = (
        select(Product)
        .join(UserScan, UserScan.barcode == Product.barcode)
        .where(UserScan.user_id == current_user.id)
        .order_by(UserScan.scanned_at.desc())
    )
    result = await session.execute(statement)
    # Use unique() since a user might scan the same product multiple times
    products = result.scalars().unique().all()
    
    # We might have duplicates if they scanned multiple times, let's keep only unique products while preserving order
    seen = set()
    unique_products = []
    for p in products:
        if p.barcode not in seen:
            seen.add(p.barcode)
            unique_products.append(p)

    return unique_products

class ProfileUpdateRequest(BaseModel):
    dietary_preferences: Optional[str] = None
    health_goals: Optional[str] = None
    allergies: Optional[List[str]] = None
    health_tags: Optional[List[str]] = None

@router.put("/profile")
async def update_profile(
    data: ProfileUpdateRequest,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """Update User Profile Data (Allergies, Diets, etc.)"""
    stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
    res = await session.execute(stmt)
    profile = res.scalars().first()

    if not profile:
        profile = UserProfile(user_id=current_user.id)
        session.add(profile)

    if data.dietary_preferences is not None:
        profile.dietary_preferences = data.dietary_preferences
    if data.health_goals is not None:
        profile.health_goals = data.health_goals
    if data.allergies is not None:
        profile.allergies = data.allergies
    if data.health_tags is not None:
        profile.health_tags = data.health_tags

    await session.commit()
    return {"status": "success", "message": "Profile updated successfully"}
