from typing import TypedDict, List, Dict, Any, Optional, Annotated
import operator
from app.models.schemas import AIAnalysisresult 

class AgentState(TypedDict):
    # Inputs
    barcode: str
    product_name: str
    ingredients: List[str]
    user_profile: str
    category_tag: str

    # Outputs
    is_food: bool
    ai_analysis: Optional[AIAnalysisresult]
    red_flag_triggered: bool
    banned_ingredients_found: Annotated[List[str], operator.add]
    
    final_response: Dict[str, Any]
