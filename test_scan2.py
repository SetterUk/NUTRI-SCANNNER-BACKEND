import httpx
import asyncio

async def test():
    try:
        async with httpx.AsyncClient(timeout=60.0) as c:
            r = await c.post("http://127.0.0.1:8000/api/scan/8901491103794")
            print(f"Status Code: {r.status_code}")
            print(f"Response: {r.text}")
    except Exception as e:
        print(f"Error: {e}")

asyncio.run(test())
