import json
from groq import AsyncGroq
from app.core.config import settings
from app.models.schemas import AIAnalysisResult

# Initialize the Groq client
client = AsyncGroq(api_key=settings.GROQ_API_KEY)

async def analyze_product_detailed(product_name: str, ingredients_text: str) -> AIAnalysisResult:
    prompt = f"""
    ROLE: Clinical Data Analyst & Nutritionist.
    
    TASK: Receive raw ingredient text. Parse it line-by-line. Extract quantities.
    
    LOGIC:
    - Classify each ingredient as Good/Bad/Neutral for general human health.
    - Calculate health_score: Start at 100. Deduct 10 points for every "Bad" ingredient. Add 5 for "Good" (max 100).
    
    DATA:
    Product: "{product_name}"
    Ingredients text: "{ingredients_text}"
    
    OUTPUT FORMAT: Return purely VALID JSON matching this schema:
    {{
        "verdict": "SMASH" or "PASS",
        "health_score": <int 0-100>,
        "summary": "<2-sentence clinical summary>",
        "ingredients_analysis": [
            {{
                "name": "<string>",
                "quantity": "<string, default 'Unknown'>",
                "status": "Good" | "Bad" | "Neutral",
                "reason": "<short scientific explanation>"
            }}
        ]
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
            health_score=0,
            summary="AI Analysis Unavailable",
            ingredients_analysis=[]
        )