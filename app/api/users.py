from typing import Any
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlmodel import select
from sqlalchemy.orm import selectinload
from app.core.database import get_session
from app.api.deps import get_current_user
from app.models.users import User, UserProfile
from app.models.schemas import UserResponse, ProfileUpdate, UserProfileResponse

router = APIRouter()

@router.get("/profile", response_model=UserResponse)
async def read_user_me(
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user)
) -> Any:
    """
    Get current user profile.
    """
    # Explicitly load profile to avoid async lazy load issues
    statement = select(User).where(User.id == current_user.id).options(selectinload(User.profile))
    result = await session.execute(statement)
    user = result.scalars().first()
    return user

@router.patch("/profile", response_model=UserProfileResponse)
async def update_user_profile(
    *,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
    profile_in: ProfileUpdate
) -> Any:
    """
    Update own profile.
    """
    # Fetch profile
    statement = select(UserProfile).where(UserProfile.user_id == current_user.id)
    db_profile = (await session.execute(statement)).scalars().first()
    
    if not db_profile:
        # Fallback create if missing (should not happen with register logic)
        db_profile = UserProfile(user_id=current_user.id)
    
    # Update data
    update_data = profile_in.model_dump(exclude_unset=True)
    for key, value in update_data.items():
        setattr(db_profile, key, value)
    
    session.add(db_profile)
    await session.commit()
    await session.refresh(db_profile)
    return db_profile
