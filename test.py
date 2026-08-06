import httpx
import asyncio

async def test():
    async with httpx.AsyncClient() as c:
        r = await c.get('https://world.openfoodfacts.org/api/v2/product/5449000000996.json', headers={'User-Agent': 'HealthEatApp/1.0'})
        print(r.status_code)
        print(r.json().get('status', 'no_status'))

asyncio.run(test())
