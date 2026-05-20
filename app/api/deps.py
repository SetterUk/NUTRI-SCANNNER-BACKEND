from typing import Optional, Dict, Any
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlmodel import select
from datetime import datetime, timedelta

from app.core.config import settings
from app.core.database import get_session
from app.models.users import User, UserProfile

import bcrypt

# Standard HTTP Bearer for mobile apps
security = HTTPBearer()

ALGORITHM = "HS256"
SECRET_KEY = settings.SECRET_KEY

def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        return bcrypt.checkpw(plain_password.encode('utf-8'), hashed_password.encode('utf-8'))
    except Exception:
        return False

def get_password_hash(password: str) -> str:
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
    return encoded_jwt

async def get_current_user(
    session: AsyncSession = Depends(get_session),
    token: HTTPAuthorizationCredentials = Depends(security)
) -> User:
    """
    Validates our custom backend JWT.
    Returns the User object if valid.
    """
    try:
        payload = jwt.decode(
            token.credentials,
            SECRET_KEY,
            algorithms=[ALGORITHM]
        )
        
        user_id_str = payload.get("sub")
        if not user_id_str:
            raise HTTPException(status_code=401, detail="Invalid token: missing subject")
        
        user_id = int(user_id_str)
        
        statement = select(User).where(User.id == user_id)
        result = await session.execute(statement)
        user = result.scalars().first()
        
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
            
        return user

    except (JWTError, ValueError) as e:
        print(f"JWT Validation Error: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
        )

security_optional = HTTPBearer(auto_error=False)

async def get_current_user_optional(
    session: AsyncSession = Depends(get_session),
    token: Optional[HTTPAuthorizationCredentials] = Depends(security_optional)
) -> Optional[User]:
    if not token or not token.credentials:
        return None
    try:
        payload = jwt.decode(
            token.credentials,
            SECRET_KEY,
            algorithms=[ALGORITHM]
        )
        user_id_str = payload.get("sub")
        if not user_id_str:
            return None
        
        user_id = int(user_id_str)
        statement = select(User).where(User.id == user_id)
        result = await session.execute(statement)
        user = result.scalars().first()
        return user
    except Exception:
        return None

