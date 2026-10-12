import json
import logging
from app.core.groq_manager import groq_manager
from app.agents.state import AgentState
from app.agents.nutritionist import analyze_product_detailed, breakdown
from app.services.scoring import compute_health_score, explain
from app.core.config import settings
from typing import Any, Dict

logger = logging.getLogger(__name__)


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
    nutrients = state.get("nutrients") or {}
    ingredients_text = state.get("ingredients_text") or ""
    nova_group = state.get("nova_group")
    nutri_score = state.get("nutri_score")

    # Fast-path 1: Deterministic Food Detection (Instant - 0ms latency)
    # If the product has nutrition info, NOVA group, NutriScore, or ingredients, it is a food product
    if nutrients or nova_group is not None or nutri_score is not None or (ingredients_text and len(ingredients_text.strip()) > 3):
        logger.info(f"Intent Agent (Fast-Path): '{product_name}' verified as FOOD via nutritional metadata.")
        return {"is_food": True}

    cat_lower = f"{category_tag} {product_name}".lower()
    
    # Fast-path 2: Obvious non-food keywords
    non_food_keywords = [
        "shampoo", "soap", "detergent", "cleaner", "lotion", "battery", "toothpaste",
        "deodorant", "bleach", "disinfectant", "cosmetic", "perfume", "skin care",
        "conditioner", "body wash", "pet food", "cat food", "dog food"
    ]
    if any(kw in cat_lower for kw in non_food_keywords):
        logger.info(f"Intent Agent (Fast-Path): '{product_name}' verified as NON-FOOD via keyword matching.")
        return {"is_food": False}

    # Fast-path 3: Obvious food keywords
    food_keywords = [
        "food", "beverage", "drink", "snack", "dairy", "cereal", "biscuit", "cookie",
        "edible", "tea", "coffee", "juice", "oil", "bread", "pasta", "sauce",
        "condiment", "spice", "flour", "fruit", "vegetable", "meat", "fish", "chocolate",
        "sweet", "chips", "noodle", "rice", "dal", "milk", "cheese", "curd", "yogurt"
    ]
    if any(kw in cat_lower for kw in food_keywords):
        logger.info(f"Intent Agent (Fast-Path): '{product_name}' verified as FOOD via category keywords.")
        return {"is_food": True}

    logger.info(f"Intent Agent classifying via LLM: '{product_name}' (category: '{category_tag}')")

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
        MAX_RETRIES = 3
        response = None
        for attempt in range(MAX_RETRIES):
            client = groq_manager.get_best_client()
            try:
                response = await client.chat.completions.create(
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
                    model="openai/gpt-oss-120b",
                    response_format={"type": "json_object"},
                    temperature=0,  # deterministic — classification must be consistent
                )
                break
            except Exception as e:
                if "429" in str(e) or "rate limit" in str(e).lower():
                    logger.warning(f"Intent Agent: Groq rate limited on attempt {attempt+1}. Switching keys...")
                    groq_manager.mark_rate_limited(client, retry_after_seconds=60.0)
                    if attempt == MAX_RETRIES - 1:
                        raise e
                else:
                    raise e

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
        product_name=state.get("product_name"),
        food_groups=state.get("food_groups") or [],
        off_allergens=state.get("off_allergens"),
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
        user_profile=state.get("user_profile")
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
            "fallback":          {
                "verdict": scoring.verdict,
                "is_good_for_health": scoring.is_good_for_health,
                "health_scale": scoring.health_scale,
                "safe_consumption_frequency": scoring.safe_consumption_frequency,
                "health_score": scoring.health_score,
                "score_breakdown": breakdown(scoring),
                **explain(scoring, state.get("product_name"), state.get("nutrients") or {}),
                "ingredients_analysis": [],
            },
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
                "verdict": None,
                "is_good_for_health": False,
                "health_reason": f"'{product_name}' doesn't look like a food or drink, so it can't be rated.",
                "health_scale": 1.0,
                "safe_consumption_frequency": "Not applicable",
                "health_score": None,
                "summary": "This looks like a non-food item. Nutrition ratings only apply to food and drinks.",
                "ingredients_analysis": [],
                "nutrition_analysis": None,
                "score_breakdown": None,
            }
        }

    # Case B: Full analysis available (the nutritionist already falls back to a deterministic
    # explanation when the LLM fails, so this is the normal path)
    analysis_result = state.get("analysis_result")
    if analysis_result:
        logger.info("Response Synthesizer: full analysis available.")
        return {"final_response": analysis_result.model_dump()}

    # Case C: scoring succeeded but building the result failed — still a complete, plain answer
    scoring_result = state.get("scoring_result")
    if scoring_result and scoring_result.get("fallback"):
        logger.warning("Response Synthesizer: using deterministic scoring explanation.")
        return {"final_response": scoring_result["fallback"]}

    # Last resort: everything failed. Returning nothing makes the API report a retryable error
    # instead of caching a fake score of 0.
    logger.error("Response Synthesizer: both scoring and LLM failed.")
    raise RuntimeError("Analysis pipeline produced no result")
