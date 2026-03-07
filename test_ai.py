import asyncio
from app.agents.nutritionist import analyze_product_detailed

async def test_ai():
    print("Testing AI...")
    try:
        # A mocked out version of Nutella ingredients for the test
        result = await analyze_product_detailed(
            "Nutella", 
            "Sugar, palm oil, hazelnuts (13%), skimmed milk powder (8.7%), fat-reduced cocoa (7.4%), emulsifier: lecithin (soya), vanillin."
        )
        print("Success! Result:")
        print(result.model_dump_json(indent=2))
    except Exception as e:
        print(f"FAILED WITH EXCEPTION: {e}")

if __name__ == "__main__":
    asyncio.run(test_ai())
