import google.generativeai as genai
import json
import os
from app.core.config import settings
from app.models.schemas import AIAnalysisresult
genai.configure(api_key=settings.GEMINI_API_KEY)

async def analyze_product(product_name: str, ingredients: list, user_profile: str = "general health"):
    model = genai.GenerativeModel("gemini-1.5-flash")

    prompt = f"""
    ROLE: You are a witty, sarcastic, and brutally honest Clinical Nutritionist.
    
    TASK: Analyze this product.
    Product: "{product_name}"
    Ingredients: {", ".join(ingredients)}
    
    USER PROFILE: "{user_profile}"
    (If the product conflicts with this profile, it is an AUTOMATIC PASS).

    OUTPUT FORMAT: Return purely VALID JSON with these keys:
    {{
        "verdict": "SMASH" (Healthy/Safe) or "PASS" (Unhealthy/Unsafe),
        "roast_or_toast": "A one-sentence funny comment. Roast it if PASS, Hype it if SMASH.",
        "reasoning": "A one-sentence scientific explanation."
    }}
    """
    try:
        response = await model.generate_content_async(
            prompt,
            generation_config={"response_mime_type": "application/json"}
        )
        return AIAnalysisresult.model_validate_json(response.text)
    
    
    except Exception as e:
        print(f"AI Analysis Failed: {e}")
        return AIAnalysisresult(
            verdict = "PASS", 
            roast_or_toast =  "I'm having a brain freeze, but this looks suspicious.", 
            reasoning = "AI Service unavailable."
        )