from google import genai
from app.core.config import settings

client = genai.Client(api_key=settings.GEMINI_API_KEY)

print("Available Models:")
for model in client.models.list():
    if "flash" in model.name:
        print(model.name)
