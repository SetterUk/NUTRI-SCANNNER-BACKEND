from pydantic import BaseModel, Field, computed_field, field_validator
from typing import List, Optional
from enum import Enum

class IngredientStatus(str, Enum):
    Good = "Good"
    Bad = "Bad"
    Neutral = "Neutral"

class IngredientAnalysis(BaseModel):
    name: str
    quantity: str = Field(default="Unknown")
    status: IngredientStatus
    reason: str

class AIAnalysisResult(BaseModel):
    verdict: str = Field(description="SMASH OR PASS")
    health_score: int = Field(ge=0, le=100)
    summary: str
    ingredients_analysis: List[IngredientAnalysis]

    @field_validator('verdict')
    @classmethod
    def clean_verdict(cls, v: str) -> str:
        clean = v.strip().upper().replace("!", "").replace(".", "")
        return clean if clean in ["SMASH", "PASS"] else "PASS"

class AlternativeProduct(BaseModel):
    name: str = Field(alias="product_name", default="Unknown Product")
    brand: str = Field(alias="brands", default="generic")
    image_url: Optional[str] = None 
    nutri_score: str = Field(alias="nutriscore_grade", default="?")
    code: str

    @computed_field
    def color_hex(self) -> str:
        """returns the hex color for the ui based on the nutriscore""" 
        colors = {
            "a": "#008000",
            "b": "#85BB2F",
            "c": "#FFD300",
            "d": "#FF7F00",
            "e": "#FF0000"
        }  
        return colors.get(self.nutri_score.lower(), "#808080")

class ProductResponse(AIAnalysisResult):
    barcode: str
    name: str
    brand: Optional[str] = None
    image_url: Optional[str] = None
    quantity: Optional[str] = None
    
    ingredients_text: Optional[str] = None
    ingredients: List[str] = []
    nutrients: Optional[dict] = None
    nutri_score: Optional[str] = None
    
    nova_group: Optional[int] = None
    nova_tags: List[str] = []
    categories: Optional[str] = None
    
    countries: Optional[str] = None
    allergens: Optional[str] = None
    additives_tags: List[str] = []
    serving_size: Optional[str] = None
    ecoscore_grade: Optional[str] = None
    nutrient_levels: Optional[dict] = None
    packaging: Optional[str] = None

    alternatives: List[AlternativeProduct] = []

    model_config = {"from_attributes": True}

    @computed_field
    def verdict_color(self) -> str:
        """Red for Pass, Green for smash"""
        return "#FF0000" if self.verdict == "PASS" else "#008000"
