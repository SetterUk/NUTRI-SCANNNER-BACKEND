from typing import TypedDict, List, Dict, Any, Optional
from app.models.schemas import AIAnalysisResult

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

    # Fields required by the scoring engine
    nova_group: Optional[int]
    nutri_score: Optional[str]
    additives_tags: List[str]
    nutrient_levels: Dict[str, Any]
    serving_size: Optional[str]

    # Populated by scoring engine before LLM call
    scoring_result: Optional[Dict[str, Any]]
