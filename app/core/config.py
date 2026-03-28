from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    PROJECT_NAME: str = "Nutri Scanner"
    VERSION: str = "1.0.0"
    DATABASE_URL: str
    GROQ_API_KEY: str
    SECRET_KEY: str
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    
    # Google Auth Configuration
    GOOGLE_CLIENT_ID: str = ""

    model_config = SettingsConfigDict(env_file=".env")

settings = Settings()
