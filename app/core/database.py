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

async def get_session():
    async with async_session_factory() as session:
        yield session
async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(SQLModel.metadata.create_all)        