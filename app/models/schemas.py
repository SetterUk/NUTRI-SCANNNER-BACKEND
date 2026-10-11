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

VERDICTS = ["GREAT", "GOOD", "OKAY", "LIMIT", "AVOID"]
# Analyses cached before the five-band scale used a yes/no verdict.
_LEGACY_VERDICTS = {"SMASH": "GOOD", "PASS": "LIMIT", "DANGEROUS": "AVOID"}

VERDICT_COLORS = {
    "GREAT": "#1E8E3E", "GOOD": "#7CB342", "OKAY": "#F9A825", "LIMIT": "#EF6C00", "AVOID": "#D32F2F",
}


class ScoreBreakdown(BaseModel):
    """Plain-language "why this score", computed deterministically from the scoring engine."""
    concerns: List[str] = []
    positives: List[str] = []
    category: Optional[str] = None
    allergens: List[str] = []
    data_confidence: Optional[float] = None


class AIAnalysisResult(BaseModel):
    verdict: Optional[str] = Field(default=None, description="GREAT, GOOD, OKAY, LIMIT or AVOID")
    is_good_for_health: Optional[bool] = Field(default=False, description="True if generally healthy to consume, False otherwise.")
    health_reason: Optional[str] = Field(default="Not analyzed yet", description="Short sentence explaining why it is good or bad.")
    health_scale: Optional[float] = Field(default=1.0, ge=1.0, le=10.0, description="Deprecated: health_score / 10, kept for older app builds.")
    safe_consumption_frequency: Optional[str] = Field(default="Unknown", description="How often this can be eaten, matching the verdict band.")
    health_score: Optional[int] = Field(default=0, ge=0, le=100)
    summary: Optional[str] = Field(default="Summary unavailable")
    ingredients_analysis: Optional[List[IngredientAnalysis]] = Field(default_factory=list)
    nutrition_analysis: Optional[NutritionAnalysis] = None
    score_breakdown: Optional[ScoreBreakdown] = None

    @field_validator('verdict', mode='before')
    @classmethod
    def clean_verdict(cls, v: Optional[str]) -> Optional[str]:
        if not v:
            return None
        clean = v.strip().upper().replace("!", "").replace(".", "")
        clean = _LEGACY_VERDICTS.get(clean, clean)
        return clean if clean in VERDICTS else None

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
    code: str
    name: str = "Unknown Product"
    brand: Optional[str] = None
    image_url: Optional[str] = None
    nutri_score: Optional[str] = None
    health_score: int
    verdict: str
    why_better: List[str] = []

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

    food_groups: Optional[List[str]] = Field(default_factory=list)
    diet_tags: Optional[List[str]] = Field(default_factory=list)

    alternatives: List[AlternativeProduct] = []
    # Allergy/diet conflicts for the signed-in user; when present the verdict is AVOID.
    personal_alerts: List[str] = []

    model_config = {"from_attributes": True}

    @computed_field
    def verdict_color(self) -> str:
        return VERDICT_COLORS.get(self.verdict or "", "#808080")

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
