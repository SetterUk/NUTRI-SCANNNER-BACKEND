from typing import Optional, List
from sqlmodel import SQLModel, Field, JSON, Relationship
from datetime import datetime

class User(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    google_id: Optional[str] = Field(default=None, index=True, unique=True)
    email: str = Field(index=True, unique=True)
    hashed_password: Optional[str] = None
    full_name: Optional[str] = None
    is_active: bool = Field(default=True)
    
    created_at: datetime = Field(default_factory=datetime.utcnow)
    
    # Relationship to profile
    profile: Optional["UserProfile"] = Relationship(back_populates="user")

class UserProfile(SQLModel, table=True):
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True, unique=True)
    
    # Physical attributes
    age: Optional[int] = None
    weight_kg: Optional[float] = Field(default=None, description="Weight in kilograms")
    height: Optional[float] = Field(default=None, description="Height in centimeters")
    gender: Optional[str] = None
    activity_level: Optional[str] = None
    
    # New Health Profile fields
    dietary_preferences: Optional[str] = None
    health_goals: Optional[str] = None
    allergies: List[str] = Field(default=[], sa_type=JSON)

    # Health tags (Diabetic, Vegan, etc.)
    health_tags: List[str] = Field(default=[], sa_type=JSON)
    
    updated_at: datetime = Field(
        default_factory=datetime.utcnow,
        sa_column_kwargs={"onupdate": datetime.utcnow}
    )
    
    # Relationship back to user
    user: Optional[User] = Relationship(back_populates="profile")
