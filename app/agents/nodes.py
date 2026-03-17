import logging
from app.agents.state import AgentState
from app.agents.nutritionist import analyze_product_detailed
from typing import Any, Dict

logger = logging.getLogger(__name__)

async def intent_agent(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Intent Classification & Hazard Filter
    FUNCTION: Performs a heuristic analysis on the 'product_name' string to validate if the item
    belongs to the 'Human Consumables' domain. It acts as a primary safety gate to prevent
    the downstream LLM agents from attempting to process hazardous non-food items (e.g., toxins, 
    cosmetics, electronic hardware) as edible products.

    Update:
        - is_food (bool): Set to False if non-food keywords are detected.
    """
    logger.info(f"Intent Agent analyzing product: {state.get('product_name')}")
    non_food_keywords = ["detergent", "shampoo", "battery", "soap", "bleach", "cleaner", "lotion", "cream"]
    product_name_lower = state.get("product_name", "").lower()
    
    is_food = True
    for kw in non_food_keywords:
        if kw in product_name_lower:
            is_food = False
            break
            
    return {"is_food": is_food}

def orchestrator_node(state: AgentState) -> str:
    """
    AGENT: Workflow Orchestrator (Router)
    FUNCTION: Evaluates the current Graph State (specifically the 'is_food' boolean) to determine
     the optimal execution path. It implements a conditional branching logic:
    
    Path A (is_food=True): Route to 'nutritionist_agent' for deep physiological analysis.
    Path B (is_food=False): Route directly to 'response_synthesizer' to generate a safety rejection payload.
    """
    logger.info(f"Orchestrator Node routing. is_food={state.get('is_food')}")
    if state.get("is_food", True):
        return "nutritionist_agent"
    return "response_synthesizer"

async def nutritionist_agent(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Clinical Nutritional Analyst (LLM-Driven)
    FUNCTION: Ingests the multi-modal product data (Raw Text, Structured Ingredients, and
    Numerical Nutritional Facts) and invokes a high-parameter Language Model (Llama-3.3-70b).
    It cross-references ingredient lists with quantitative macronutrient/micronutrient data 
    to calculate a granular Health Score and determine metabolic impact.

    Update:
        - analysis_result (AIAnalysisResult): Structured Pydantic object containing clinical insights.
    """
    logger.info("Nutritionist Agent starting deep analysis via LLM.")
    result = await analyze_product_detailed(
        product_name=state.get("product_name", "Unknown"),
        ingredients_text=state.get("ingredients_text", ""),
        ingredients_list=state.get("ingredients", []),
        nutrients=state.get("nutrients", {})
    )
    return {"analysis_result": result}

async def response_synthesizer(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Response Serialization & Format Unifier
    FUNCTION: Aggregates the computed attributes from all preceding agents into a final, 
    type-safe API response. It handles logical fallbacks (AI downtime or non-food rejection)
    and ensures the 'final_response' state variable conforms to the 'ProductResponse'
    Pydantic schema required by the mobile frontend.
    """
    logger.info("Response Synthesizer compiling final payload.")
    if not state.get("is_food", True):
        return {
            "final_response": {
                "verdict": "PASS",
                "is_good_for_health": False,
                "health_reason": "Not a food product",
                "health_scale": 1.0,
                "safe_consumption_frequency": "Never",
                "health_score": 0,
                "summary": "Item identified as non-food.",
                "ingredients_analysis": [],
                "nutrition_analysis": {
                    "energy_estimation": "Not applicable",
                    "macronutrient_balance": "Not applicable"
                }
            }
        }
        
    analysis_result = state.get("analysis_result")
    if analysis_result:
        return {"final_response": analysis_result.model_dump()}
    else:
        return {
            "final_response": {
                "verdict": "PASS",
                "is_good_for_health": False,
                "health_reason": "AI Analysis Unavailable",
                "health_scale": 1.0,
                "safe_consumption_frequency": "Unknown",
                "health_score": 0,
                "summary": "AI Analysis Unavailable",
                "ingredients_analysis": [],
                "nutrition_analysis": {
                    "energy_estimation": "Unavailable",
                    "macronutrient_balance": "Unavailable"
                }
            }
        }
