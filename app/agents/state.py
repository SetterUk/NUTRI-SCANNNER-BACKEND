from typing import TypedDict, List, Dict, Any, Optional
from app.models.schemas import AIAnalysisResult

class AgentState(TypedDict):
    product_name: str
    ingredients_text: str
    is_food: bool
    analysis_result: Optional[AIAnalysisResult]
    final_response: Dict[str, Any]
    
    # Keeping extra fields from previous routes for compatibility
    barcode: str
    ingredients: List[str]
    nutrients: Dict[str, Any]
    category_tag: str
