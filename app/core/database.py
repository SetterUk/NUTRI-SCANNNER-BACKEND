from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker
from sqlmodel import SQLModel
from app.core.config import settings

# Import models to ensure they are registered with SQLModel.metadata
from app.models.products import Product, UserAddedProduct
from app.models.users import User, UserProfile


engine = create_async_engine(
    settings.DATABASE_URL,
    echo=False,
    future=True,
    pool_pre_ping=True,
    pool_recycle=3600
)

async_session_factory = sessionmaker(
    bind = engine,
    class_ = AsyncSession,
    expire_on_commit=False
)

from sqlalchemy import text

async def get_session():
    async with async_session_factory() as session:
        yield session

async def init_db():
    async with engine.begin() as conn:
        # Create any tables that don't exist yet
        await conn.run_sync(SQLModel.metadata.create_all)
        
        # Apply micro-migrations/schema upgrades to existing tables
        try:
            # 1. Add new columns
            await conn.execute(text("ALTER TABLE product ADD COLUMN IF NOT EXISTS source VARCHAR DEFAULT 'OFF';"))
            
            # 2. Relax constraints for columns that became Optional
            await conn.execute(text("ALTER TABLE product ALTER COLUMN verdict DROP NOT NULL;"))
            await conn.execute(text("ALTER TABLE product ALTER COLUMN is_good_for_health DROP NOT NULL;"))
            await conn.execute(text("ALTER TABLE product ALTER COLUMN health_reason DROP NOT NULL;"))
            await conn.execute(text("ALTER TABLE product ALTER COLUMN health_scale DROP NOT NULL;"))
            await conn.execute(text("ALTER TABLE product ALTER COLUMN safe_consumption_frequency DROP NOT NULL;"))
            await conn.execute(text("ALTER TABLE product ALTER COLUMN health_score DROP NOT NULL;"))
            await conn.execute(text("ALTER TABLE product ALTER COLUMN summary DROP NOT NULL;"))
            print("Successfully applied automated schema updates.")
        except Exception as e:
            print(f"Automated schema update notice (might already be applied): {e}")        