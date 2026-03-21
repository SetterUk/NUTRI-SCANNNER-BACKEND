"""
LLM Nutritionist — Text Generation Only
========================================
The scoring engine (scoring.py) has already locked:
  - health_score, verdict, health_scale, is_good_for_health, safe_consumption_frequency

This module's ONLY job is to ask the LLM to EXPLAIN those locked scores in plain language.
The LLM outputs: health_reason, summary, ingredients_analysis, nutrition_analysis.
It does NOT output any numeric scores.
"""

import json
from groq import AsyncGroq
from app.core.config import settings
from app.models.schemas import AIAnalysisResult
from app.services.scoring import ScoringResult

client = AsyncGroq(api_key=settings.GROQ_API_KEY)


async def analyze_product_detailed(
    product_name: str,
    ingredients_text: str,
    ingredients_list: list,
    nutrients: dict,
    scoring: ScoringResult,
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

    prompt = f"""
ROLE: Clinical Nutritionist — Text Explanation Specialist.

CONTEXT:
A deterministic scoring engine has already computed all numeric scores for "{product_name}".
Your job is ONLY to explain those scores in clear, clinical language.
You MUST NOT change or recompute any of the scores below.

LOCKED SCORES (do not alter):
- health_score: {scoring.health_score} / 100
- verdict: "{scoring.verdict}"
- health_scale: {scoring.health_scale} / 10.0
- is_good_for_health: {str(scoring.is_good_for_health).lower()}
- safe_consumption_frequency: "{scoring.safe_consumption_frequency}"
- data_confidence: {confidence_label}
- category_group: "{scoring.category_group}"

SCORE BREAKDOWN (what drove the score):
Pillar scores:
{pillar_str}

Deductions applied:
{deductions_str}

Bonuses applied:
{bonuses_str}

RAW PRODUCT DATA (use for ingredient-level explanations):
Product: "{product_name}"
Ingredients text: "{ingredients_text}"
Structured ingredients: {ingredients_list_str}
Nutritional facts (per 100g): {nutrients_str}

YOUR TASKS:
1. Write `health_reason`: 1–2 sentences explaining WHY the product scored {scoring.health_score}/100.
   - Reference the actual deductions by name and number (e.g. "High sugar at 56g/100g cost -12 points")
   - Reference the category context ("{scoring.category_group}" group thresholds were used)
   - Do NOT mention the score number itself — explain the reasons, not the outcome

2. Write `summary`: 2 sentences. First: what the product is and its dominant nutritional characteristic.
   Second: practical takeaway for the consumer based on "{scoring.safe_consumption_frequency}".

3. Write `ingredients_analysis`: For EACH ingredient in the structured list, output:
   - name: ingredient name (clean, human-readable)
   - quantity: exact value from nutritional facts if available, else "Not specified"
   - status: "Good", "Bad", or "Neutral" — must align with the deductions/bonuses above
   - reason: 1 sentence scientific explanation referencing actual quantities where possible

4. Write `nutrition_analysis`:
   - energy_estimation: How much energy this product provides. Use exact kcal from nutritional facts.
     Explain the metabolic impact (quick energy vs sustained, empty calories vs nutrient-dense).
   - macronutrient_balance: Summarise the carbs/protein/fat ratio. State the actual numbers.
     Comment on whether the balance is appropriate for the "{scoring.category_group}" food category.

OUTPUT FORMAT: Return ONLY valid JSON matching this exact schema:
{{
    "health_reason": "<string>",
    "summary": "<string>",
    "ingredients_analysis": [
        {{
            "name": "<string>",
            "quantity": "<string>",
            "status": "Good" | "Bad" | "Neutral",
            "reason": "<string>"
        }}
    ],
    "nutrition_analysis": {{
        "energy_estimation": "<string>",
        "macronutrient_balance": "<string>"
    }}
}}
"""

    try:
        chat_completion = await client.chat.completions.create(
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You are a clinical nutritionist AI. You explain pre-computed nutrition scores "
                        "in plain, accurate language. You only output valid JSON. "
                        "You never recompute or override the locked numeric scores provided to you."
                    )
                },
                {
                    "role": "user",
                    "content": prompt,
                }
            ],
            model="llama-3.3-70b-versatile",
            response_format={"type": "json_object"},
        )

        response_text = chat_completion.choices[0].message.content
        llm_output = json.loads(response_text)

        # Merge locked scores with LLM-generated text
        return AIAnalysisResult(
            verdict=scoring.verdict,
            is_good_for_health=scoring.is_good_for_health,
            health_scale=scoring.health_scale,
            safe_consumption_frequency=scoring.safe_consumption_frequency,
            health_score=scoring.health_score,
            health_reason=llm_output.get("health_reason", "Analysis unavailable."),
            summary=llm_output.get("summary", "Analysis unavailable."),
            ingredients_analysis=llm_output.get("ingredients_analysis", []),
            nutrition_analysis=llm_output.get("nutrition_analysis", {
                "energy_estimation": "Unavailable",
                "macronutrient_balance": "Unavailable"
            }),
        )

    except Exception as e:
        print(f"LLM text generation failed: {e}")
        # Fallback: use locked scores with minimal text
        return AIAnalysisResult(
            verdict=scoring.verdict,
            is_good_for_health=scoring.is_good_for_health,
            health_scale=scoring.health_scale,
            safe_consumption_frequency=scoring.safe_consumption_frequency,
            health_score=scoring.health_score,
            health_reason="AI explanation unavailable — scores computed from nutritional data.",
            summary="Scores are based on nutritional analysis. AI explanation service is temporarily unavailable.",
            ingredients_analysis=[],
            nutrition_analysis={
                "energy_estimation": "Unavailable",
                "macronutrient_balance": "Unavailable"
            }
        )
