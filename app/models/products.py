from typing import Optional, List
from sqlmodel import SQLModel, Field, JSON
from datetime import datetime

class Product(SQLModel, table=True):
    barcode: str = Field(primary_key=True)

    name: str
    brand: Optional[str] = None
    image_url: Optional[str] = None

    ingredients: List[str] = Field(sa_type=JSON)
    nutri_score: Optional[str] = None
    Category_tag: Optional[str] = None

    verdict: str
    roast_or_toast: str
    reasoning: str

    scan_count: int = Field(default=1)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)