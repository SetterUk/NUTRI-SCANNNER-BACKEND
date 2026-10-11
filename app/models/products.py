from typing import Optional, List, Dict, Any
from sqlmodel import SQLModel, Field, JSON
from datetime import datetime

class Product(SQLModel, table=True):
    barcode: str = Field(primary_key=True)

    name: str
    brand: Optional[str] = None
    image_url: Optional[str] = None

    ingredients: List[str] = Field(sa_type=JSON)
    ingredients_text: Optional[str] = None
    nutrients: Optional[Dict[str, Any]] = Field(default=None, sa_type=JSON)
    quantity: Optional[str] = None
    nova_group: Optional[int] = None
    nova_tags: Optional[List[str]] = Field(default=None, sa_type=JSON)
    nutri_score: Optional[str] = None
    categories: Optional[str] = None

    # Generic (profile-free) AI analysis. Personalized results live in PersonalizedAnalysis;
    # they must never be cached here, or one user's verdict is shown to everyone.
    verdict: Optional[str] = None
    is_good_for_health: Optional[bool] = None
    health_reason: Optional[str] = None
    health_scale: Optional[float] = None
    safe_consumption_frequency: Optional[str] = None
    health_score: Optional[int] = None
    summary: Optional[str] = None
    ingredients_analysis: List[Dict[str, Any]] = Field(default=[], sa_type=JSON)
    nutrition_analysis: Dict[str, Any] = Field(default={}, sa_type=JSON)

    # Bumped when the cached generic analysis format/meaning changes; rows with an older
    # version are re-analysed. v2 = first version guaranteed to be profile-free.
    analysis_version: int = Field(default=0)

    # Metadata
    source: str = Field(default="OFF") # "OFF" or "MANUAL"
    scan_count: int = Field(default=1)
    
    countries: Optional[str] = None
    allergens: Optional[str] = None
    additives_tags: Optional[List[str]] = Field(default=None, sa_type=JSON)
    serving_size: Optional[str] = None
    ecoscore_grade: Optional[str] = None
    nutrient_levels: Optional[Dict[str, Any]] = Field(default=None, sa_type=JSON)
    packaging: Optional[str] = None

    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

class PersonalizedAnalysis(SQLModel, table=True):
    """AI verdict for one user + product, valid only while the user's profile is unchanged."""
    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    barcode: str = Field(foreign_key="product.barcode", index=True)
    profile_hash: str
    payload: Dict[str, Any] = Field(default={}, sa_type=JSON)
    created_at: datetime = Field(default_factory=datetime.utcnow)

class UserAddedProduct(SQLModel, table=True):
    """
    Table for tracking products that were not found in Open Food Facts.
    All fields are optional as per requirements.
    """
    id: Optional[int] = Field(default=None, primary_key=True)
    name: Optional[str] = None
    quantity: Optional[str] = None
    barcode: Optional[str] = None
    created_at: datetime = Field(default_factory=datetime.utcnow)
