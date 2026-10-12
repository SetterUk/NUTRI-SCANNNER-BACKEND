"""
LLM Nutritionist — Text Generation Only
========================================
The scoring engine (scoring.py) has already locked:
  - health_score, verdict, health_scale, is_good_for_health, safe_consumption_frequency

This module's ONLY job is to ask the LLM to EXPLAIN those locked scores in plain language.
The LLM outputs: health_reason, summary, ingredients_analysis, nutrition_analysis.
It does NOT output any numeric scores or verdicts. Allergy and diet conflicts are applied
deterministically in services/personal.py.
"""

import json
import logging
import time
from app.core.config import settings
from app.core.groq_manager import groq_manager
from app.models.schemas import AIAnalysisResult
from app.services.scoring import ScoringResult, CATEGORY_LABELS, explain

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)


async def analyze_product_detailed(
    product_name: str,
    ingredients_text: str,
    ingredients_list: list,
    nutrients: dict,
    scoring: ScoringResult,
    user_profile: dict = None,
) -> AIAnalysisResult:
    """
    Call the LLM to generate text explanations for a pre-computed ScoringResult.
    All numeric fields are taken directly from `scoring` — the LLM cannot override them.
    """
    ingredients_list_str = json.dumps(ingredients_list) if ingredients_list else "[]"
    nutrients_str = json.dumps(nutrients, indent=2) if nutrients else "{}"

    deductions_str = json.dumps(scoring.deductions, indent=2)
    bonuses_str    = json.dumps(scoring.bonuses, indent=2)
    pillar_str     = json.dumps(scoring.pillar_scores, indent=2)

    confidence_label = (
        "HIGH (≥80% — full clinical analysis)"   if scoring.data_confidence >= 0.8 else
        "MEDIUM (40–79% — analysis based on partial data)" if scoring.data_confidence >= 0.4 else
        "LOW (<40% — limited data, treat as estimate)"
    )

    category_label = CATEGORY_LABELS.get(scoring.category_group, "packaged foods")
    concerns_str = "\n".join(f"- {c}" for c in scoring.concerns) or "- none"
    positives_str = "\n".join(f"- {p}" for p in scoring.positives) or "- none"
    if user_profile:
        profile_block = (
            json.dumps(user_profile, indent=2) +
            "\nRelate the product to this profile (goals, conditions such as diabetes or hypertension, diet) "
            "in `health_reason` and `summary`. Allergy and diet conflicts are checked separately by the app; "
            "do not invent allergens that are not in the ingredients."
        )
    else:
        profile_block = "No profile. Give general advice for an Indian adult."

    prompt = f"""
ROLE: Nutritionist explaining a food score to an Indian consumer in plain, friendly English.

A deterministic scoring engine already rated "{product_name}". Do not recompute or contradict it.
- Score: {scoring.health_score}/100, verdict {scoring.verdict}
- Suggested amount: {scoring.safe_consumption_frequency}
- Compared against: {category_label}
- Data completeness: {confidence_label}

WHAT LOWERED THE SCORE (biggest first):
{concerns_str}

WHAT HELPED:
{positives_str}

Full breakdown (for reference only, never quote point values or internal names):
{deductions_str}
{bonuses_str}

USER PROFILE:
{profile_block}

PRODUCT DATA:
Ingredients text: "{ingredients_text}"
Structured ingredients: {ingredients_list_str}
Nutrition per 100 g: {nutrients_str}

RULES:
- Only describe a nutrient as high or low if the lists above say so. Never call something "high" that
  is not in "WHAT LOWERED THE SCORE".
- Use grams per 100 g with at most one decimal. No jargon: never write "NOVA", "pillar", "floor",
  "deduction", "points", "category group" or the score number.
- If the ingredients text is empty, return [] for ingredients_analysis and say the ingredient list is
  missing. Never guess ingredients.

TASKS:
1. health_reason: 1–2 sentences on why it rated this way, naming the top concerns and positives.
2. summary: 2 sentences. First: what the product is and what stands out. Second: a practical tip
   (portion, when to eat it, or what to pair it with) consistent with "{scoring.safe_consumption_frequency}".
3. ingredients_analysis: for each ingredient (max 12, most important first): name, quantity
   ("Not specified" unless known), status "Good" | "Bad" | "Neutral", reason (1 short sentence).
4. nutrition_analysis: energy_estimation (kcal per 100 g and what that means for a typical portion),
   macronutrient_balance (carbs/protein/fat per 100 g and whether that suits {category_label}).

Return ONLY valid JSON:
{{
    "health_reason": "<string>",
    "summary": "<string>",
    "ingredients_analysis": [{{"name": "<string>", "quantity": "<string>", "status": "Good", "reason": "<string>"}}],
    "nutrition_analysis": {{"energy_estimation": "<string>", "macronutrient_balance": "<string>"}}
}}
"""

    try:
        logger.info(f"Nutritionist Agent: Calling Groq API for '{product_name}'...")
        logger.debug(f"Groq API Prompt:\n{prompt}")
        
        start_time = time.time()
        
        MAX_RETRIES = 3
        chat_completion = None
        for attempt in range(MAX_RETRIES):
            client = groq_manager.get_best_client()
            try:
                chat_completion = await client.chat.completions.create(
                    messages=[
                        {
                            "role": "system",
                            "content": (
                                "You are an expert culinary nutrition and dietary intelligence AI. "
                                "You explain pre-computed food quality scores, additives, and ingredients in plain, clear language. "
                                "You only output valid JSON. You provide practical food insights and ingredient clarity for the user's dietary profile. "
                                "You never recompute or override the locked numeric scores provided to you."
                            )
                        },
                        {
                            "role": "user",
                            "content": prompt,
                        }
                    ],
                    model="openai/gpt-oss-120b",
                    response_format={"type": "json_object"},
                )
                break
            except Exception as e:
                if "429" in str(e) or "rate limit" in str(e).lower():
                    logger.warning(f"Nutritionist Agent: Groq rate limited on attempt {attempt+1}. Switching keys...")
                    groq_manager.mark_rate_limited(client, retry_after_seconds=60.0)
                    if attempt == MAX_RETRIES - 1:
                        raise e
                else:
                    raise e
        
        duration = time.time() - start_time

        response_text = chat_completion.choices[0].message.content
        logger.info(f"Nutritionist Agent: Groq API responded in {duration:.2f}s.")
        logger.debug(f"Groq API Response:\n{response_text}")
        
        llm_output = json.loads(response_text)
        fallback = explain(scoring, product_name, nutrients)
        nutrition = llm_output.get("nutrition_analysis") or {}
        if not isinstance(nutrition, dict):
            nutrition = {}

        # Merge locked scores with LLM-generated text
        return AIAnalysisResult(
            **_locked(scoring),
            health_reason=_text(llm_output.get("health_reason")) or fallback["health_reason"],
            summary=_text(llm_output.get("summary")) or fallback["summary"],
            ingredients_analysis=_clean_ingredients(llm_output.get("ingredients_analysis")),
            nutrition_analysis={
                "energy_estimation": _text(nutrition.get("energy_estimation"))
                                     or fallback["nutrition_analysis"]["energy_estimation"],
                "macronutrient_balance": _text(nutrition.get("macronutrient_balance"))
                                         or fallback["nutrition_analysis"]["macronutrient_balance"],
            },
        )

    except Exception as e:
        logger.error(f"Nutritionist Agent: LLM text generation failed: {e}", exc_info=True)
        # The deterministic explanation is complete on its own; users never see an "unavailable" message.
        return AIAnalysisResult(**_locked(scoring), **explain(scoring, product_name, nutrients), ingredients_analysis=[])


def _locked(scoring: ScoringResult) -> dict:
    return {
        "verdict": scoring.verdict,
        "is_good_for_health": scoring.is_good_for_health,
        "health_scale": scoring.health_scale,
        "safe_consumption_frequency": scoring.safe_consumption_frequency,
        "health_score": scoring.health_score,
        "score_breakdown": breakdown(scoring),
    }


def breakdown(scoring: ScoringResult) -> dict:
    return {
        "concerns": scoring.concerns,
        "positives": scoring.positives,
        "category": CATEGORY_LABELS.get(scoring.category_group, "packaged foods"),
        "allergens": scoring.allergen_warnings,
        "data_confidence": scoring.data_confidence,
    }


def _text(v) -> str:
    return v.strip() if isinstance(v, str) else ""


def _clean_ingredients(items) -> list:
    """Drops malformed rows instead of failing the whole analysis on one bad status value."""
    out = []
    for it in items if isinstance(items, list) else []:
        if not isinstance(it, dict) or not _text(it.get("name")):
            continue
        status = _text(it.get("status")).capitalize()
        out.append({
            "name": _text(it.get("name")),
            "quantity": _text(it.get("quantity")) or "Not specified",
            "status": status if status in ("Good", "Bad", "Neutral") else "Neutral",
            "reason": _text(it.get("reason")),
        })
    return out[:15]
