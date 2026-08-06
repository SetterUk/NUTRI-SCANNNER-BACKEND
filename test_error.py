import asyncio
from app.models.schemas import ProductResponse
from app.models.products import Product

def test_validation():
    # Simulate DB object with default/fallback data generated in routes.py
    new_product = Product(
        barcode="123",
        name="Test",
        verdict="PASS",
        is_good_for_health=False,
        health_reason="Analysis failed",
        health_scale=1.0,
        safe_consumption_frequency="Unknown",
        health_score=0,
        summary="Analysis failed",
        ingredients_analysis=[],
        nutrition_analysis={}  # This empty dict is inserted when fallback agent_output is used!
    )
    
    try:
        response = ProductResponse.model_validate(new_product)
        print("Success")
    except Exception as e:
        print("Validation Error!")
        print(e)

if __name__ == "__main__":
    test_validation()
