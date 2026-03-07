from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PROJECT_NAME: str = "Nutri Scanner"
    VERSION: str = "1.0.0"
    DATABASE_URL: str
    GROQ_API_KEY: str

    class Config:
        env_file = ".env"

settings = Settings()
