import logging
from app.agents.state import AgentState
from app.agents.nutritionist import analyze_product_detailed
from typing import Any, Dict

logger = logging.getLogger(__name__)

async def intent_agent(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Intent Classifier & Safety Guardrail
    DESCRIPTION: Scans the incoming product name to determine if the item is an edible food product. 
    It prevents the downstream Nutritionist AI from attempting to analyze hazardous non-food items like 
    detergents, batteries, or cosmetics as if they were food. 
    
    Returns:
        Dict: Updates the `is_food` state variable. True if edible, False if hazardous/non-food.
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
    DESCRIPTION: Acts as the state machine's decision engine. It inspects the `is_food` Boolean produced 
    by the Intent Agent and routes the execution flow accordingly. 
    
    Returns:
        str: The name of the next node to execute ("nutritionist_agent" or "response_synthesizer").
    """
    logger.info(f"Orchestrator Node routing. is_food={state.get('is_food')}")
    if state.get("is_food", True):
        return "nutritionist_agent"
    return "response_synthesizer"

async def nutritionist_agent(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Clinical Nutritionist AI
    DESCRIPTION: The core intelligence of the application. It takes the raw ingredient text and the 
    product name, then invokes a large language model (Gemini 1.5 Flash) heavily prompted as a 
    Clinical Data Analyst. It performs a deep physiological analysis on the ingredients.
    
    Returns:
        Dict: Updates the `analysis_result` state variable with a strictly typed AIAnalysisResult object.
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
    AGENT: Final Response Synthesizer
    DESCRIPTION: Aggregates the data produced throughout the Graph's lifecycle into the final, 
    Android-ready JSON payload. It handles edge cases, such as fast-failing non-food items, and 
    maps the AI's internal state structures into the clean dictionary required by the API route.
    
    Returns:
        Dict: Populates the `final_response` state variable.
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
