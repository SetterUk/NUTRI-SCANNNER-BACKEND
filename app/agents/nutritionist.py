import json
from groq import AsyncGroq
from app.core.config import settings
from app.models.schemas import AIAnalysisResult

# Initialize the Groq client
client = AsyncGroq(api_key=settings.GROQ_API_KEY)

async def analyze_product_detailed(
    product_name: str, 
    ingredients_text: str,
    ingredients_list: list = None,
    nutrients: dict = None
) -> AIAnalysisResult:
    ingredients_list_str = json.dumps(ingredients_list) if ingredients_list else "[]"
    nutrients_str = json.dumps(nutrients, indent=2) if nutrients else "{}"
    
    prompt = f"""
    ROLE: Clinical Data Analyst & Nutritionist.
    
    TASK: You are given raw ingredient text, a structured list of ingredients, and a detailed dictionary of nutritional facts (per 100g and per serving). You must cross-reference the ingredients with their precise nutritional values to determine health impacts.
    
    LOGIC:
    - Evaluate EACH ingredient's status (Good/Bad/Neutral) explicitly based on the precise QUANTITY and NUTRITIONAL FACTS provided.
    - For EVERY ingredient in the list (e.g., sugar, salt, fat, vitamins, additives, fiber), actively search the 'Nutritional Facts' dictionary for matching keys (e.g., 'sugars_100g', 'salt_100g', 'fat_100g', 'vitamin-c_100g', 'fiber_100g') to determine its exact quantity and threshold.
    - If a specific nutrient fact is missing for an ingredient, estimate its severity based on its position in the Raw Ingredients text (listed by weight).
    - Calculate health_score: Start at 100. Deduct points for excessive amounts of ANY unhealthy nutrient (high sugar, high saturated fats, high sodium, harmful additives) according to clinical guidelines. Add points for beneficial nutrients (fiber, protein, vitamins).
    - Determine 'is_good_for_health': True if the product overall is healthy, False if the macros/ingredients are unhealthy.
    - Provide a short 'health_reason' explaining the 'is_good_for_health' verdict based on the *actual numbers* from the Nutrients Facts for ALL major components.
    - Assign a 'health_scale' from 1.0 (terrible) to 10.0 (excellent) based on the comprehensive nutritional profile.
    - Suggest a 'safe_consumption_frequency' (e.g., 'Daily', 'Twice a week', 'Rarely').
    - Write a detailed 'nutrition_analysis' block focusing strictly on the 'energy-kcal' (or 'energy') and macros ('carbohydrates', 'proteins', 'fats') found in the Nutritional Facts dictionary. State exactly how many calories and macros are present, and explain the physical energy outcome (e.g. 'Provides a quick burst of energy due to 66g of carbs, but lacks sustained energy due to low protein').
    
    DATA:
    Product: "{product_name}"
    Raw Ingredients text: "{ingredients_text}"
    Structured Ingredients: {ingredients_list_str}
    Nutritional Facts: {nutrients_str}
    
    OUTPUT FORMAT: Return purely VALID JSON matching this schema:
    {{
        "verdict": "SMASH" or "PASS",
        "is_good_for_health": true or false,
        "health_reason": "<short sentence explaining why it is healthy or unhealthy>",
        "health_scale": <float 1.0 - 10.0>,
        "safe_consumption_frequency": "<string, e.g., 'Daily', 'Max 2 times a week'>",
        "health_score": <int 0-100>,
        "summary": "<2-sentence clinical summary>",
        "ingredients_analysis": [
            {{
                "name": "<string>",
                "quantity": "<string, default 'Unknown'>",
                "status": "Good" | "Bad" | "Neutral",
                "reason": "<short scientific explanation>"
            }}
        ],
        "nutrition_analysis": {{
            "energy_estimation": "<string explaining energy output based on kcal/carbs/fats>",
            "macronutrient_balance": "<string summarizing the balance of macros>"
        }}
    }}
    """
    try:
        chat_completion = await client.chat.completions.create(
            messages=[
                {
                    "role": "system",
                    "content": "You are a clinical nutritionist AI that only outputs valid JSON."
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
        print(f"\n--- AI RESPONSE DATA ---\n{response_text}\n------------------------\n")
        return AIAnalysisResult.model_validate_json(response_text)
    
    except Exception as e:
        print(f"AI Analysis Failed: {e}")
        return AIAnalysisResult(
            verdict="PASS", 
            is_good_for_health=False,
            health_reason="AI Analysis Unavailable",
            health_scale=1.0,
            safe_consumption_frequency="Unknown",
            health_score=0,
            summary="AI Analysis Unavailable",
            ingredients_analysis=[],
            nutrition_analysis={
                "energy_estimation": "Unavailable",
                "macronutrient_balance": "Unavailable"
            }
        )