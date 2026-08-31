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

class NutritionAnalysis(BaseModel):
    energy_estimation: str = Field(default="", description="Explanation of how much energy the user will get after eating this, based on kcal and macros.")
    macronutrient_balance: str = Field(default="", description="Summary of the carbs, proteins, and fats balance.")

class AIAnalysisResult(BaseModel):
    verdict: Optional[str] = Field(default="PASS", description="SMASH OR PASS")
    is_good_for_health: Optional[bool] = Field(default=False, description="True if generally healthy to consume, False otherwise.")
    health_reason: Optional[str] = Field(default="Not analyzed yet", description="Short sentence explaining why it is good or bad.")
    health_scale: Optional[float] = Field(default=1.0, ge=1.0, le=10.0, description="1 to 10 scale, where 10 is excellent and 1 is terrible.")
    safe_consumption_frequency: Optional[str] = Field(default="Unknown", description="How often this can safely be consumed (e.g., 'Daily', 'Twice a week').")
    health_score: Optional[int] = Field(default=0, ge=0, le=100)
    summary: Optional[str] = Field(default="Summary unavailable")
    ingredients_analysis: Optional[List[IngredientAnalysis]] = Field(default_factory=list)
    nutrition_analysis: Optional[NutritionAnalysis] = None

    @field_validator('verdict', mode='before')
    @classmethod
    def clean_verdict(cls, v: Optional[str]) -> str:
        if not v:
            return "PASS"
        clean = v.strip().upper().replace("!", "").replace(".", "")
        return clean if clean in ["SMASH", "PASS"] else "PASS"

class ReportMissingRequest(BaseModel):
    name: Optional[str] = None
    quantity: Optional[str] = None
    barcode: Optional[str] = None

class ContributeProductRequest(BaseModel):
    name: str
    ingredients_text: Optional[str] = None
    image_base64: Optional[str] = None

class ManualProductRequest(BaseModel):
    barcode: str
    name: str
    ingredients_text: str
    brand: Optional[str] = None
    quantity: Optional[str] = None

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
    ingredients: Optional[List[str]] = Field(default_factory=list)
    nutrients: Optional[dict] = None
    nutri_score: Optional[str] = None
    
    nova_group: Optional[int] = None
    nova_tags: Optional[List[str]] = Field(default_factory=list)
    categories: Optional[str] = None
    
    countries: Optional[str] = None
    allergens: Optional[str] = None
    additives_tags: Optional[List[str]] = Field(default_factory=list)
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

# --- USER & PROFILE SCHEMAS ---

class UserRegister(BaseModel):
    email: str
    password: str
    full_name: Optional[str] = None

class UserLogin(BaseModel):
    email: str
    password: str

class ProfileUpdate(BaseModel):
    age: Optional[int] = None
    weight_kg: Optional[float] = None
    height: Optional[float] = None
    gender: Optional[str] = None
    activity_level: Optional[str] = None
    dietary_preferences: Optional[str] = None
    health_goals: Optional[str] = None
    allergies: Optional[List[str]] = None
    health_tags: Optional[List[str]] = None
    
    # Calculated Targets
    bmi: Optional[float] = None
    bmr: Optional[float] = None
    tdee: Optional[float] = None
    daily_calories: Optional[float] = None
    daily_protein: Optional[float] = None
    daily_carbs: Optional[float] = None
    daily_fat: Optional[float] = None
    daily_fiber: Optional[float] = None
    daily_water: Optional[float] = None

class UserProfileResponse(BaseModel):
    age: Optional[int] = None
    weight_kg: Optional[float] = None
    height: Optional[float] = None
    gender: Optional[str] = None
    activity_level: Optional[str] = None
    dietary_preferences: Optional[str] = None
    health_goals: Optional[str] = None
    allergies: List[str] = []
    health_tags: List[str] = []
    
    # Calculated Targets
    bmi: Optional[float] = None
    bmr: Optional[float] = None
    tdee: Optional[float] = None
    daily_calories: Optional[float] = None
    daily_protein: Optional[float] = None
    daily_carbs: Optional[float] = None
    daily_fat: Optional[float] = None
    daily_fiber: Optional[float] = None
    daily_water: Optional[float] = None

class UserResponse(BaseModel):
    id: int
    email: str
    full_name: Optional[str] = None
    profile: Optional[UserProfileResponse] = None

    model_config = {"from_attributes": True}

class Token(BaseModel):
    access_token: str
    token_type: str

class TokenData(BaseModel):
    user_id: Optional[str] = None
