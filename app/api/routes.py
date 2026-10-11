from typing import Optional
from fastapi import APIRouter, HTTPException, Depends
from sqlmodel import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_session
from app.models.products import Product, UserAddedProduct, PersonalizedAnalysis
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
import base64
import hashlib
import json
import os
import time
from datetime import datetime
from fastapi import Request
from app.core.limiter import limiter

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

router = APIRouter()

@router.get("/ping")
async def ping():
    return {"ping": "pong"}

class TokenData(BaseModel):
    id_token: str

@router.post("/auth/register")
@limiter.limit("5/minute")
async def register_user(request: Request, data: UserRegister, session: AsyncSession = Depends(get_session)):
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
@limiter.limit("5/minute")
async def login_user(request: Request, data: UserLogin, session: AsyncSession = Depends(get_session)):
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
@limiter.limit("20/minute")
async def scan_product(
    barcode: str,
    request: Request,
    session: AsyncSession = Depends(get_session),
    current_user: Optional[User] = Depends(get_current_user_optional)
):
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

    # 3. ANALYSIS: generic results are cached on the product and shared; personalized results are
    #    cached per user and only reused while that user's profile is unchanged. (Previously the first
    #    scanner's personalized verdict, including allergy conclusions, was cached and shown to everyone.)
    profile_ctx = _profile_context(profile)
    personalized = any(v for v in profile_ctx.values())

    agent_output = None
    cached_personal = None
    profile_hash = None
    if personalized:
        profile_hash = hashlib.sha256(json.dumps(profile_ctx, sort_keys=True, default=str).encode()).hexdigest()
        cached_stmt = select(PersonalizedAnalysis).where(
            PersonalizedAnalysis.user_id == current_user.id,
            PersonalizedAnalysis.barcode == barcode,
        )
        cached_personal = (await session.execute(cached_stmt)).scalars().first()
        if cached_personal and cached_personal.profile_hash == profile_hash:
            logger.info(f"Serving cached personalized analysis for user {current_user.id}, barcode {barcode}")
            agent_output = cached_personal.payload
    elif product.health_score is not None and (product.analysis_version or 0) >= GENERIC_ANALYSIS_VERSION:
        logger.info(f"Serving cached generic analysis for barcode {barcode}")
        agent_output = {k: getattr(product, k) for k in ANALYSIS_FIELDS}

    if agent_output is None:
        agent_output = await _run_analysis(product, profile_ctx if personalized else {})
        if agent_output is not None:
            if personalized:
                if cached_personal:
                    cached_personal.profile_hash = profile_hash
                    cached_personal.payload = agent_output
                    cached_personal.created_at = datetime.utcnow()
                    session.add(cached_personal)
                else:
                    session.add(PersonalizedAnalysis(
                        user_id=current_user.id, barcode=barcode,
                        profile_hash=profile_hash, payload=agent_output,
                    ))
            else:
                for k in ANALYSIS_FIELDS:
                    setattr(product, k, agent_output.get(k))
                product.analysis_version = GENERIC_ANALYSIS_VERSION
        else:
            agent_output = {
                "verdict": None,
                "health_score": None,
                "summary": "We couldn't analyse this product right now. Please try again in a moment.",
                "ingredients_analysis": [],
            }

    await session.commit()
    await session.refresh(product)

    # 4. LOG THE SCAN FOR THE USER
    if current_user:
        session.add(UserScan(user_id=current_user.id, barcode=barcode))
        await session.commit()

    # Build response: Product Data + (possibly personalized) Analysis
    response_data = product.model_dump()
    response_data.update(agent_output)
    return response_data


ANALYSIS_FIELDS = [
    "verdict", "is_good_for_health", "health_reason", "health_scale",
    "safe_consumption_frequency", "health_score", "summary",
    "ingredients_analysis", "nutrition_analysis",
]
GENERIC_ANALYSIS_VERSION = 2


def _profile_context(profile: Optional[UserProfile]) -> dict:
    if not profile:
        return {}
    return {
        "age": profile.age,
        "weight_kg": profile.weight_kg,
        "height": profile.height,
        "gender": profile.gender,
        "activity_level": profile.activity_level,
        "dietary_preferences": profile.dietary_preferences,
        "health_tags": profile.health_tags or [],
        "allergies": profile.allergies or [],
        "health_goals": profile.health_goals,
    }


async def _run_analysis(product: Product, profile_ctx: dict) -> Optional[dict]:
    """Runs the LangGraph workflow. Returns None on failure (never cache a failed analysis)."""
    initial_state = {
        "barcode": product.barcode,
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
        "user_profile": profile_ctx or None,
    }
    try:
        final_state = await nutrition_app_workflow.ainvoke(initial_state)
        output = final_state.get("final_response") or {}
        return {k: output.get(k) for k in ANALYSIS_FIELDS} | {
            k: v for k, v in output.items() if k not in ANALYSIS_FIELDS
        }
    except Exception as e:
        logger.error(f"Agent Workflow Failed for {product.barcode}: {e}", exc_info=True)
        return None


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

    ingredients_text = data.ingredients_text or ""
    ingredients_list = [i.strip() for i in ingredients_text.split(",") if i.strip()]

    if not product:
        # If it truly doesn't exist, create a skeleton product
        product = Product(
            barcode=barcode,
            name=data.name,
            ingredients_text=ingredients_text,
            ingredients=ingredients_list,
            source="MANUAL"
        )
        session.add(product)
    else:
        # Update existing
        product.name = data.name
        product.ingredients_text = ingredients_text
        product.ingredients = ingredients_list
        
        # Invalidate AI Cache!
        product.health_score = None
        product.verdict = None
        product.summary = None
        product.ingredients_analysis = []
        product.nutrition_analysis = {}
        
    # Handle Image Upload
    if data.image_base64 and len(data.image_base64) > 7_000_000:
        raise HTTPException(status_code=413, detail="Image too large (max ~5 MB).")
    if data.image_base64:
        try:
            # Create static/images directory if it doesn't exist
            image_dir = os.path.join("app", "static", "images")
            os.makedirs(image_dir, exist_ok=True)
            
            # Remove base64 header if present (e.g. data:image/jpeg;base64,...)
            img_data = data.image_base64
            if "," in img_data:
                img_data = img_data.split(",")[1]
            
            image_bytes = base64.b64decode(img_data)
            
            # Save file
            filename = f"{barcode}_{int(time.time())}.jpg"
            filepath = os.path.join(image_dir, filename)
            with open(filepath, "wb") as f:
                f.write(image_bytes)
                
            # Set URL
            product.image_url = f"/static/images/{filename}"
        except Exception as e:
            logger.error(f"Failed to process image upload for {barcode}: {e}")

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

@router.get("/profile")
async def get_profile(
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """Get the current user's profile"""
    stmt = select(UserProfile).where(UserProfile.user_id == current_user.id)
    res = await session.execute(stmt)
    profile = res.scalars().first()

    if not profile:
        profile = UserProfile(user_id=current_user.id)
        session.add(profile)
        await session.commit()
        await session.refresh(profile)

    return profile

class ProfileUpdateRequest(BaseModel):
    preferred_name: Optional[str] = None
    dietary_preferences: Optional[str] = None
    health_goals: Optional[str] = None
    allergies: Optional[List[str]] = None
    health_tags: Optional[List[str]] = None
    age: Optional[int] = None
    height: Optional[float] = None
    weight_kg: Optional[float] = None
    gender: Optional[str] = None
    activity_level: Optional[str] = None
    medical_reports: Optional[str] = None

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

    if data.preferred_name is not None:
        profile.preferred_name = data.preferred_name.strip() or None
    if data.dietary_preferences is not None:
        profile.dietary_preferences = data.dietary_preferences.strip() or None
    if data.health_goals is not None:
        profile.health_goals = data.health_goals.strip() or None
    if data.allergies is not None:
        profile.allergies = data.allergies
    if data.health_tags is not None:
        profile.health_tags = data.health_tags
    if data.age is not None:
        profile.age = data.age
    if data.height is not None:
        profile.height = data.height
    if data.weight_kg is not None:
        profile.weight_kg = data.weight_kg
    if data.gender is not None:
        profile.gender = data.gender.strip() or None
    if data.activity_level is not None:
        profile.activity_level = data.activity_level.strip() or None
    if data.medical_reports is not None:
        profile.medical_reports = data.medical_reports.strip()[:20000] or None

    await session.commit()
    await session.refresh(profile)
    return {"status": "success", "message": "Profile updated successfully"}

class ChatMessage(BaseModel):
    role: str
    content: str

class ChatRequest(BaseModel):
    messages: List[ChatMessage]

@router.post("/chat")
@limiter.limit("20/minute")
async def chat_with_nutritionist(
    request: Request,
    data: ChatRequest,
    current_user: User = Depends(get_current_user)
):
    from app.core.groq_manager import groq_manager
    client = groq_manager.get_best_client()
    try:
        allowed_roles = {"system", "user", "assistant"}
        raw_messages = [
            {"role": m.role, "content": m.content[:8000]}
            for m in data.messages[-24:]
            if m.role in allowed_roles
        ]
        # Reinforce system prompt if present or inject supportive guidance
        has_system = any(m["role"] == "system" for m in raw_messages)
        if not has_system:
            raw_messages.insert(0, {
                "role": "system",
                "content": (
                    "You are NutriBot, an expert culinary nutritionist and meal planning coach. "
                    "Provide practical Indian meal plans, food swaps, and macro calculations based on ICMR/IFCT dietary guidelines. "
                    "Always answer nutritional, dietary, and meal planning queries directly."
                )
            })

        response = await client.chat.completions.create(
            model="openai/gpt-oss-120b",
            messages=raw_messages
        )
        content = response.choices[0].message.content or ""
        return {"response": content}
    except Exception as e:
        logger.error(f"Chat error: {e}")
        raise HTTPException(status_code=500, detail="Error generating response")


@router.delete("/account")
async def delete_account(
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
):
    """Permanently deletes the user, their profile, scan history and personalized analyses."""
    from sqlalchemy import delete as sa_delete
    uid = current_user.id
    await session.execute(sa_delete(PersonalizedAnalysis).where(PersonalizedAnalysis.user_id == uid))
    await session.execute(sa_delete(UserScan).where(UserScan.user_id == uid))
    await session.execute(sa_delete(UserProfile).where(UserProfile.user_id == uid))
    await session.execute(sa_delete(User).where(User.id == uid))
    await session.commit()
    logger.info(f"Deleted account {uid}")
    return {"status": "deleted"}
