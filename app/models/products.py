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

    verdict: str
    roast_or_toast: str
    reasoning: str

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