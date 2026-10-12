from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import field_validator

class Settings(BaseSettings):
    PROJECT_NAME: str = "Nutri Scanner"
    VERSION: str = "1.0.0"
    DATABASE_URL: str
    GROQ_API_KEYS: str = ""
    
    @property
    def groq_api_keys_list(self) -> list[str]:
        if not self.GROQ_API_KEYS:
            return []
        return [k.strip() for k in self.GROQ_API_KEYS.split(",") if k.strip()]
    # No default: with a public fallback key anyone could forge a login token for any user.
    # Generate one with: python -c "import secrets; print(secrets.token_urlsafe(48))"
    SECRET_KEY: str
    # A daily-habit app: weekly forced sign-ins were silently breaking saves (expired token -> 401).
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days
    
    # Google Auth Configuration
    GOOGLE_CLIENT_ID: str = ""

    SENTRY_DSN: str = ""

    model_config = SettingsConfigDict(env_file=".env")

    @field_validator("DATABASE_URL", mode="before")
    @classmethod
    def validate_database_url(cls, v: str) -> str:
        if not v:
            return v
        if v.startswith("postgres://"):
            v = v.replace("postgres://", "postgresql+psycopg://", 1)
        elif v.startswith("postgresql://"):
            v = v.replace("postgresql://", "postgresql+psycopg://", 1)
        return v

settings = Settings()
