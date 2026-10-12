import os

# The app reads these at import time. Set them explicitly (not setdefault) so a developer's .env
# can never point tests at the production database or send test errors to production Sentry.
os.environ["DATABASE_URL"] = "postgresql+psycopg://test:test@localhost/test"
os.environ["SECRET_KEY"] = "test-secret"
os.environ["SENTRY_DSN"] = ""
os.environ["GROQ_API_KEYS"] = "test-key-not-used"  # every LLM call is stubbed in tests
