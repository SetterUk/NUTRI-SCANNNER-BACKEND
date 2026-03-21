import json
import logging
from groq import AsyncGroq
from app.agents.state import AgentState
from app.agents.nutritionist import analyze_product_detailed
from app.services.scoring import compute_health_score
from app.core.config import settings
from typing import Any, Dict

logger = logging.getLogger(__name__)
_groq = AsyncGroq(api_key=settings.GROQ_API_KEY)


# ---------------------------------------------------------------------------
# AGENT 1: Intent Classification
# ---------------------------------------------------------------------------

async def intent_agent(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Intent Classification & Hazard Filter
    FUNCTION: Uses an LLM to determine whether the scanned product is a
    human-consumable food or beverage. Replaces brittle keyword matching with
    semantic understanding — handles edge cases like protein shakes, medicinal
    honey, cooking ingredients, and borderline items (e.g. toothpaste, baby formula).

    Prompt strategy:
    - Minimal context: product name + category tag only (fast, cheap call)
    - Forces binary JSON output — no ambiguity
    - System role locks the model to classification only

    Update:
        - is_food (bool): True if human-consumable, False otherwise.
    """
    product_name = state.get("product_name", "Unknown")
    category_tag = state.get("category_tag", "")

    logger.info(f"Intent Agent classifying: '{product_name}' (category: '{category_tag}')")

    prompt = f"""You are classifying a scanned product barcode result.

Product name: "{product_name}"
Category tag: "{category_tag}"

Determine if this product is something a human would consume as food or drink.

CLASSIFY AS FOOD (is_food: true):
- Any food, snack, meal, beverage, ingredient, condiment, supplement, protein powder
- Baby formula and baby food
- Cooking oils, vinegars, spices, flours
- Alcoholic beverages (beer, wine, spirits)
- Medicinal foods, nutrition bars, meal replacements

CLASSIFY AS NON-FOOD (is_food: false):
- Cleaning products (detergents, bleach, disinfectants)
- Cosmetics and personal care (shampoo, lotion, toothpaste, deodorant)
- Medicines and pharmaceuticals (pills, syrups for treatment, not nutrition)
- Electronic components, batteries, hardware
- Pet food and animal feed
- Industrial chemicals

Return ONLY this JSON with no explanation:
{{"is_food": true}} or {{"is_food": false}}"""

    try:
        response = await _groq.chat.completions.create(
            messages=[
                {
                    "role": "system",
                    "content": "You are a strict product classifier. Output only valid JSON. No explanation."
                },
                {
                    "role": "user",
                    "content": prompt
                }
            ],
            model="llama-3.3-70b-versatile",
            response_format={"type": "json_object"},
            temperature=0,  # deterministic — classification must be consistent
        )

        result = json.loads(response.choices[0].message.content)
        is_food = bool(result.get("is_food", True))
        logger.info(f"Intent Agent classified '{product_name}' as {'FOOD' if is_food else 'NON-FOOD'}")
        return {"is_food": is_food}

    except Exception as e:
        logger.warning(f"Intent Agent LLM call failed: {e}. Defaulting to is_food=True.")
        # Safe default: if classification fails, proceed with analysis
        # It's better to over-analyse than to incorrectly block a food product
        return {"is_food": True}


# ---------------------------------------------------------------------------
# ROUTER: Orchestrator
# ---------------------------------------------------------------------------

def orchestrator_node(state: AgentState) -> str:
    """
    ROUTER: Workflow Orchestrator
    FUNCTION: Reads the is_food boolean from the intent_agent and routes
    the graph to the appropriate next node.

    Path A (is_food=True)  → nutritionist_agent (scoring + LLM analysis)
    Path B (is_food=False) → response_synthesizer (rejection payload)
    """
    is_food = state.get("is_food", True)
    logger.info(f"Orchestrator routing: is_food={is_food}")
    return "nutritionist_agent" if is_food else "response_synthesizer"


# ---------------------------------------------------------------------------
# AGENT 2: Nutritionist (Scoring Engine + LLM Explainer)
# ---------------------------------------------------------------------------

async def nutritionist_agent(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Clinical Nutritional Analyst
    FUNCTION: Two-phase pipeline —

    Phase 1 — Deterministic Scoring Engine (no LLM):
        Runs compute_health_score() with all available OpenFoodFacts data.
        Produces locked numeric scores: health_score, verdict, health_scale,
        safe_consumption_frequency, is_good_for_health.

    Phase 2 — LLM Text Generation:
        Passes the locked scores + score breakdown + raw product data to the LLM.
        LLM outputs ONLY human-readable explanations: health_reason, summary,
        ingredients_analysis, nutrition_analysis.
        The LLM cannot override the locked numeric fields.

    Update:
        - scoring_result (dict): Full output of the scoring engine for logging/debugging.
        - analysis_result (AIAnalysisResult): Merged object — locked scores + LLM text.
    """
    logger.info("Nutritionist Agent: Phase 1 — running scoring engine.")

    scoring = compute_health_score(
        nutrients=state.get("nutrients", {}),
        nova_group=state.get("nova_group"),
        nutri_score=state.get("nutri_score"),
        additives_tags=state.get("additives_tags", []),
        nutrient_levels=state.get("nutrient_levels", {}),
        ingredients=state.get("ingredients", []),
        ingredients_text=state.get("ingredients_text", ""),
        categories=state.get("category_tag"),
        serving_size=state.get("serving_size"),
    )

    logger.info(
        f"Scoring engine: score={scoring.health_score}, verdict={scoring.verdict}, "
        f"confidence={scoring.data_confidence}, group={scoring.category_group}"
    )

    logger.info("Nutritionist Agent: Phase 2 — LLM text generation.")
    result = await analyze_product_detailed(
        product_name=state.get("product_name", "Unknown"),
        ingredients_text=state.get("ingredients_text", ""),
        ingredients_list=state.get("ingredients", []),
        nutrients=state.get("nutrients", {}),
        scoring=scoring,
    )

    return {
        "scoring_result": {
            "health_score":      scoring.health_score,
            "verdict":           scoring.verdict,
            "pillar_scores":     scoring.pillar_scores,
            "deductions":        scoring.deductions,
            "bonuses":           scoring.bonuses,
            "data_confidence":   scoring.data_confidence,
            "category_group":    scoring.category_group,
            "alcohol_capped":    scoring.alcohol_capped,
        },
        "analysis_result": result,
    }


# ---------------------------------------------------------------------------
# AGENT 3: Response Synthesizer
# ---------------------------------------------------------------------------

async def response_synthesizer(state: AgentState) -> Dict[str, Any]:
    """
    AGENT: Response Serialization & Format Unifier
    FUNCTION: Assembles the final API payload from agent state. Handles
    three cases:

    Case A — Non-food detected by intent_agent:
        Returns a structured rejection payload. Score is 0.

    Case B — Full analysis available (scoring + LLM both succeeded):
        Returns analysis_result.model_dump() — all fields populated.

    Case C — Scoring succeeded but LLM text generation failed:
        Returns locked scores from scoring_result with fallback text.
        IMPORTANT: Does NOT zero out the scores — the scoring engine
        already produced valid data. Only the text fields are missing.
    """
    logger.info("Response Synthesizer compiling final payload.")

    # Case A: Non-food
    if not state.get("is_food", True):
        product_name = state.get("product_name", "this item")
        logger.info(f"Response Synthesizer: non-food rejection for '{product_name}'")
        return {
            "final_response": {
                "verdict": "PASS",
                "is_good_for_health": False,
                "health_reason": f"'{product_name}' is not a food or beverage product and cannot be nutritionally evaluated.",
                "health_scale": 1.0,
                "safe_consumption_frequency": "Not applicable",
                "health_score": 0,
                "summary": (
                    f"This product has been identified as a non-food item. "
                    f"No nutritional analysis is applicable."
                ),
                "ingredients_analysis": [],
                "nutrition_analysis": {
                    "energy_estimation": "Not applicable — non-food product.",
                    "macronutrient_balance": "Not applicable — non-food product."
                }
            }
        }

    # Case B: Full analysis available
    analysis_result = state.get("analysis_result")
    if analysis_result:
        logger.info("Response Synthesizer: full analysis available.")
        return {"final_response": analysis_result.model_dump()}

    # Case C: LLM failed but scoring succeeded — use locked scores, fallback text
    scoring_result = state.get("scoring_result")
    if scoring_result:
        logger.warning("Response Synthesizer: LLM failed, falling back to scoring engine output.")
        score = scoring_result.get("health_score", 0)
        verdict = scoring_result.get("verdict", "PASS")
        scale = round(score / 10.0, 1)
        top_deduction = (scoring_result.get("deductions") or [{}])[0]
        return {
            "final_response": {
                "verdict": verdict,
                "is_good_for_health": score >= 60,
                "health_reason": (
                    f"Score of {score}/100 based on nutritional analysis. "
                    f"Primary factor: {top_deduction.get('reason', 'multiple nutritional parameters')}. "
                    f"AI explanation service is temporarily unavailable."
                ),
                "health_scale": scale,
                "safe_consumption_frequency": (
                    "Daily" if score >= 80 else
                    "3–4 times per week" if score >= 65 else
                    "Weekly" if score >= 50 else
                    "Rarely" if score >= 35 else
                    "Avoid"
                ),
                "health_score": score,
                "summary": (
                    f"Nutritional scoring completed with a score of {score}/100 (verdict: {verdict}). "
                    f"AI-generated explanation is unavailable — scores are based on {int(scoring_result.get('data_confidence', 0) * 100)}% data confidence."
                ),
                "ingredients_analysis": [],
                "nutrition_analysis": {
                    "energy_estimation": "AI explanation unavailable.",
                    "macronutrient_balance": "AI explanation unavailable."
                }
            }
        }

    # Last resort: everything failed
    logger.error("Response Synthesizer: both scoring and LLM failed.")
    return {
        "final_response": {
            "verdict": "PASS",
            "is_good_for_health": False,
            "health_reason": "Analysis pipeline failed. Data could not be processed.",
            "health_scale": 1.0,
            "safe_consumption_frequency": "Unknown",
            "health_score": 0,
            "summary": "Our analysis system encountered an error. Please try scanning again.",
            "ingredients_analysis": [],
            "nutrition_analysis": {
                "energy_estimation": "Unavailable",
                "macronutrient_balance": "Unavailable"
            }
        }
    }
