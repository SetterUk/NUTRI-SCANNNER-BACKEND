from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import field_validator

class Settings(BaseSettings):
    PROJECT_NAME: str = "Nutri Scanner"
    VERSION: str = "1.0.0"
    DATABASE_URL: str
    GROQ_API_KEY: str = ""
    SECRET_KEY: str = "supersecretfallbackkeyfornutriscannerapi"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    
    # Google Auth Configuration
    GOOGLE_CLIENT_ID: str = ""

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
