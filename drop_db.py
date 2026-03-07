import asyncio
from sqlalchemy import text
from app.core.database import engine

async def drop_tables():
    async with engine.begin() as conn:
        print("Dropping table `product`...")
        await conn.execute(text("DROP TABLE IF EXISTS product CASCADE;"))
        print("Table dropped.")

if __name__ == "__main__":
    asyncio.run(drop_tables())
