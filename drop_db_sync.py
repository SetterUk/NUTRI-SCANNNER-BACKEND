from sqlalchemy import create_engine, text
from app.core.config import settings

# Create a synchronous URL from the async URL
sync_url = settings.DATABASE_URL.replace("+psycopg", "")

engine = create_engine(sync_url, echo=True)

def reset_db():
    with engine.begin() as conn:
        print("Dropping table `product`...")
        conn.execute(text("DROP TABLE IF EXISTS product CASCADE;"))
        print("Table dropped.")

if __name__ == "__main__":
    reset_db()
