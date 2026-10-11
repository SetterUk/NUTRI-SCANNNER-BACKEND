from typing import TypedDict, List, Dict, Any, Optional
from app.models.schemas import AIAnalysisResult

# LangGraph silently drops input keys that are not declared here, so every key the nodes read
# must be listed (user_profile was missing, and scans were never actually personalized).
class AgentState(TypedDict):
    product_name: str
    ingredients_text: str
    is_food: bool
    analysis_result: Optional[AIAnalysisResult]
    final_response: Dict[str, Any]

    barcode: str
    ingredients: List[str]
    nutrients: Dict[str, Any]
    category_tag: str
    food_groups: List[str]
    off_allergens: Optional[str]

    # Fields required by the scoring engine
    nova_group: Optional[int]
    nutri_score: Optional[str]
    additives_tags: List[str]
    nutrient_levels: Dict[str, Any]
    serving_size: Optional[str]

    user_profile: Optional[Dict[str, Any]]

    # Populated by scoring engine before LLM call
    scoring_result: Optional[Dict[str, Any]]
