import os

# The app reads these at import time; tests never connect to a database.
os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://test:test@localhost/test")
os.environ.setdefault("SECRET_KEY", "test-secret")
