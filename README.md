# HealthHeat 🔥

**Most nutrition apps tell people what is healthy. HealthHeat understands what THIS person needs, understands what THIS person ate, identifies what THIS person is missing, and tells them exactly how to bridge that gap using foods they can realistically eat.**

HealthHeat is a next-generation Personal Nutrition Intelligence platform designed specifically for the Indian diet. Built on an offline-first, local-AI architecture, it acts as your personal nutritionist in your pocket.

## Hackathon Features

1. **Deterministic Nutrition Engine**: Hard-math gap analysis for Macros and Micros (Calories, Protein, Fiber, Calcium, Iron, etc.) based on Indian physiological baselines (ICMR guidelines).
2. **"What Am I Missing?"**: Don't just track calories. Instantly see which nutrients you are critically low on today.
3. **"Fix My Nutrition"**: AI-powered recommendation engine that filters through a highly-normalized database of Indian foods and recipes to find the exact meal that bridges your daily gap, strictly adhering to your budget, dietary restrictions, and allergies.
4. **Intelligent Barcode Scanner (Personal Score)**: Standard apps give a product a general "health score." HealthHeat gives it a **Personal Score**. If you are vegan or allergic to peanuts, a "healthy" protein bar drops to a Personal Score of 0.
5. **Local SLM Voice Coach**: Uses MediaPipe GenAI and an on-device Small Language Model to let you literally talk to your nutritionist ("Maine do roti khayi"). The SLM extracts intent, queries the local SQLite database, and responds with personalized, deterministic advice.

## Architecture

HealthHeat runs entirely on your device for absolute privacy:
- **ETL Pipeline**: Python scripts normalize raw nutritional data (IFCT subsets) into a structured relational schema (`healthheat.db`).
- **Storage**: Android Room ORM consumes the pre-packaged `.db` file.
- **Engine**: Pure Kotlin business logic calculates Gaps and Recommendations. No LLM hallucinations.
- **AI**: Local MediaPipe LLM handles Intent Extraction and Conversational formatting, strictly grounded by the determinisitc Nutrition Engine.

## Running the App
- Ensure Android Studio is installed with NDK/CMake if required by MediaPipe.
- Open the `frontend_android` directory.
- Build and run on a physical device for Camera (Scanner) and Microphone (Voice) capabilities.
