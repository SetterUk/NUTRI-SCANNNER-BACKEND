# Nutri Scanner Backend — Complete Project Documentation

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture & High-Level Design](#3-architecture--high-level-design)
4. [End-to-End Request Flow](#4-end-to-end-request-flow)
5. [Directory Structure](#5-directory-structure)
6. [Database Schema](#6-database-schema)
7. [Authentication System](#7-authentication-system)
8. [API Endpoints](#8-api-endpoints)
9. [OpenFoodFacts Integration](#9-openfoodfacts-integration)
10. [LangGraph Agentic Workflow](#10-langgraph-agentic-workflow)
11. [Deterministic Scoring Engine](#11-deterministic-scoring-engine)
12. [LLM Text Generation (Groq)](#12-llm-text-generation-groq)
13. [Personalization System](#13-personalization-system)
14. [Deployment (Docker + Render)](#14-deployment-docker--render)
15. [Key Design Decisions](#15-key-design-decisions)
16. [Interview Q&A Preparation](#16-interview-qa-preparation)

---

## 1. Project Overview

**Nutri Scanner** is a backend API for a mobile food-scanning app. Users scan a product barcode with their phone, and the backend returns:

- Full product info (name, brand, image, ingredients, nutrients)
- A **health score (0–100)** computed deterministically from nutritional data
- A **verdict**: `SMASH` (score ≥ 60, eat it) or `PASS` (avoid)
- Per-ingredient analysis with Good / Bad / Neutral classification
- Safe consumption frequency (Daily / Weekly / Rarely / Avoid)
- Plain-language AI explanation of why the product scored the way it did
- Personalized analysis based on the user's dietary profile

The core idea: **food scanning + AI nutritional intelligence + personalization**, all as a production-ready async Python API.

---

## 2. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Web Framework | **FastAPI** | Async-native, automatic OpenAPI docs, Pydantic validation |
| ASGI Server | **Uvicorn** | Production-grade ASGI server for FastAPI |
| ORM | **SQLModel** | Combines SQLAlchemy + Pydantic — one model class for DB and API |
| Database | **PostgreSQL** (Neon serverless) | Serverless Postgres — no server to manage, scales to zero |
| DB Driver | **psycopg (v3, async)** | Modern async PostgreSQL driver |
| AI Orchestration | **LangGraph** | Graph-based multi-agent workflow with conditional routing |
| LLM | **Groq API** (llama-3.3-70b-versatile) | Ultra-fast inference — near-zero latency for JSON generation |
| External Food Data | **OpenFoodFacts API** | Free, open crowdsourced food database with barcode lookup |
| HTTP Client | **httpx** | Async HTTP client for calling OpenFoodFacts |
| Auth | **JWT (python-jose) + bcrypt** | Stateless auth; bcrypt for password hashing |
| Google Auth | **google-auth** | Verifies Google ID tokens from mobile OAuth flow |
| Deployment | **Docker + Render** | Containerized deployment on Render's free/paid tier |
| Config | **pydantic-settings** | Type-safe environment variable management |

---

## 3. Architecture & High-Level Design

```
Mobile App
    |
    | POST /api/scan/{barcode}
    v
FastAPI (Uvicorn)
    |
    |-- Auth Middleware (optional JWT) --> User & UserProfile loaded
    |
    |-- DB Cache Check (Product table) ──> Hit: use cached product data
    |                                  ──> Miss: call OpenFoodFacts API
    |
    |-- LangGraph Workflow ─────────────────────────────────────────
    |       |                                                       |
    |   intent_agent                                     orchestrator_node
    |   (LLM: is this food?)                            (routes based on is_food)
    |       |                                                       |
    |       |── YES ──> nutritionist_agent ──> response_synthesizer
    |       |               |
    |       |           Phase 1: scoring engine (deterministic, no LLM)
    |       |           Phase 2: LLM text generation (Groq)
    |       |
    |       |── NO ──> response_synthesizer (returns rejection payload)
    |
    |-- Merge product data + agent output
    |-- Return ProductResponse (JSON)
    v
Mobile App renders result
```

### Key Architectural Principle: Deterministic Scores + LLM Explanations

The most important design decision in this project is the **separation of scoring from explanation**:

- The **scoring engine** (`scoring.py`) computes all numeric scores using hard-coded rules. It is pure Python with no LLM — so scores are consistent, reproducible, and explainable.
- The **LLM** (`nutritionist.py`) receives the locked scores and is only allowed to generate human-readable text explanations. It cannot change the numbers.

This prevents "LLM hallucination" from affecting health scores — a critical correctness requirement for a health app.

---

## 4. End-to-End Request Flow

Here is exactly what happens when a barcode is scanned:

**Step 1 — Auth (optional)**
The request may include a `Bearer` JWT token. If present, `get_current_user_optional` validates it and loads the `User` + `UserProfile` from the database. If absent, the request still proceeds with `profile = None` (anonymous mode).

**Step 2 — Cache Check**
The `Product` table is queried by `barcode`. If found, the cached product is used and `scan_count` is incremented. This avoids repeat API calls for popular products.

**Step 3 — OpenFoodFacts Fetch (cache miss only)**
`get_product_from_api(barcode)` makes an async HTTP GET to `https://world.openfoodfacts.org/api/v2/product/{barcode}.json`. It extracts ~15 fields (name, brand, nutrients, NOVA group, Nutri-Score, additives, allergens, etc.) and creates a new `Product` row.

**Step 4 — Build Agent State**
All product data plus the user's profile (dietary preferences, health tags, allergies, health goals) are packed into a `AgentState` TypedDict.

**Step 5 — LangGraph Workflow**
`nutrition_app_workflow.ainvoke(initial_state)` runs the 3-node async graph:

- **Node 1 — intent_agent**: Calls Groq LLM to classify is_food (True/False)
- **Router — orchestrator_node**: Reads `is_food` and routes the graph
- **Node 2 — nutritionist_agent** (food path only):
  - Phase 1: Calls `compute_health_score()` — pure Python scoring
  - Phase 2: Calls `analyze_product_detailed()` — Groq LLM for text
- **Node 3 — response_synthesizer**: Assembles the final JSON payload

**Step 6 — Merge & Return**
`product.model_dump()` + `agent_output` are merged into a single `ProductResponse` dict and returned.

---

## 5. Directory Structure

```
nutri-scanner-backend/
│
├── app/
│   ├── main.py                   # FastAPI app, lifespan, CORS, router registration
│   │
│   ├── api/
│   │   ├── routes.py             # All scan/auth endpoints (POST /scan, /auth/*)
│   │   ├── users.py              # User profile endpoints (GET/PATCH /users/profile)
│   │   └── deps.py               # Auth dependencies: JWT, bcrypt, get_current_user
│   │
│   ├── agents/
│   │   ├── workflow.py           # LangGraph StateGraph definition + compile
│   │   ├── nodes.py              # Agent node implementations
│   │   ├── state.py              # AgentState TypedDict
│   │   └── nutritionist.py       # Groq LLM call + prompt engineering
│   │
│   ├── services/
│   │   ├── openfoodfacts.py      # httpx client for OpenFoodFacts API
│   │   └── scoring.py            # 5-pillar deterministic scoring engine
│   │
│   ├── models/
│   │   ├── products.py           # Product, UserAddedProduct SQLModel tables
│   │   ├── users.py              # User, UserProfile SQLModel tables
│   │   └── schemas.py            # Pydantic schemas (request/response bodies)
│   │
│   └── core/
│       ├── config.py             # pydantic-settings (env vars)
│       └── database.py           # Async SQLAlchemy engine, session factory, init_db
│
├── Dockerfile                    # Docker container definition
├── requirements.txt              # Python dependencies
└── PROJECT_DOCUMENTATION.md     # This file
```

---

## 6. Database Schema

### Table: `product` (primary cache)

| Column | Type | Notes |
|---|---|---|
| `barcode` | VARCHAR (PK) | Barcode string — natural primary key |
| `name` | VARCHAR | Product name |
| `brand` | VARCHAR | Brand name |
| `image_url` | VARCHAR | Product image URL from OpenFoodFacts |
| `ingredients` | JSON | Structured list of ingredient strings |
| `ingredients_text` | TEXT | Raw ingredients text as printed on label |
| `nutrients` | JSON | Full nutriments dict from OpenFoodFacts (100g values) |
| `quantity` | VARCHAR | Package size (e.g., "200g", "500ml") |
| `nova_group` | INT | NOVA processing level: 1=unprocessed, 4=ultra-processed |
| `nutri_score` | VARCHAR | Official Nutri-Score grade: A/B/C/D/E |
| `additives_tags` | JSON | List of E-number additive tags |
| `allergens` | VARCHAR | Allergens string |
| `nutrient_levels` | JSON | high/moderate/low dict for sugars, fat, salt, sat-fat |
| `serving_size` | VARCHAR | Serving size string (e.g., "30g") |
| `ecoscore_grade` | VARCHAR | Environmental impact grade |
| `categories` | VARCHAR | Primary category tag |
| `countries` | VARCHAR | Countries where sold |
| `packaging` | VARCHAR | Packaging material |
| `source` | VARCHAR | "OFF" (OpenFoodFacts) or "MANUAL" |
| `scan_count` | INT | How many times this barcode was scanned |
| `verdict` | VARCHAR | Cached SMASH/PASS (optional) |
| `health_score` | INT | Cached score (optional) |
| `created_at` | TIMESTAMP | First scan time |
| `updated_at` | TIMESTAMP | Last update time |

### Table: `user`

| Column | Type | Notes |
|---|---|---|
| `id` | INT (PK) | Auto-incremented |
| `email` | VARCHAR (unique) | Login email |
| `hashed_password` | VARCHAR | bcrypt hash (null for Google users) |
| `google_id` | VARCHAR (unique) | Google account ID (null for email users) |
| `full_name` | VARCHAR | Display name |
| `is_active` | BOOL | Account status |
| `created_at` | TIMESTAMP | Registration time |

### Table: `userprofile` (1:1 with user)

| Column | Type | Notes |
|---|---|---|
| `id` | INT (PK) | |
| `user_id` | INT (FK → user.id, unique) | 1:1 relationship |
| `age` | INT | |
| `weight_kg` | FLOAT | |
| `height` | FLOAT | In centimeters |
| `gender` | VARCHAR | |
| `activity_level` | VARCHAR | |
| `dietary_preferences` | VARCHAR | e.g., "vegan", "keto" |
| `health_goals` | VARCHAR | e.g., "weight loss", "muscle gain" |
| `allergies` | JSON | List of allergy strings |
| `health_tags` | JSON | e.g., ["Diabetic", "Lactose Intolerant"] |

### Table: `useraddedproduct`

Logs barcodes that were not found in OpenFoodFacts, submitted by users via the `/report-missing` endpoint.

---

## 7. Authentication System

Three auth methods are supported, all producing the same backend JWT:

### 7a. Email / Password Registration (`POST /api/auth/register`)
1. Check if email already exists in `user` table
2. Hash password with bcrypt (`bcrypt.hashpw`)
3. Create `User` row, flush to get `user.id`
4. Create a blank `UserProfile` row (1:1 auto-created at registration)
5. Sign a JWT with `{"sub": str(user.id)}`, 7-day expiry (HS256)
6. Return `{access_token, token_type, user_id}`

### 7b. Email / Password Login (`POST /api/auth/login`)
1. Fetch user by email
2. `bcrypt.checkpw(plain, hashed)` — constant-time comparison prevents timing attacks
3. On success, return JWT

### 7c. Google OAuth (`POST /api/auth/google`)
1. Mobile app completes Google Sign-In natively and gets a Google `id_token`
2. Backend calls `google.oauth2.id_token.verify_oauth2_token()` — verifies signature, expiry, and audience against `GOOGLE_CLIENT_ID`
3. If user with `google_id` doesn't exist, create one (no password)
4. Return backend JWT — from here, the mobile app uses the same JWT for all calls

### JWT Validation (`get_current_user` dependency)
- `HTTPBearer` extracts the `Authorization: Bearer <token>` header
- `jose.jwt.decode()` verifies the signature and expiry
- Looks up user by `int(payload["sub"])` in database
- `get_current_user_optional` — same logic but returns `None` instead of raising 401 if no token is present. Used on the scan endpoint to support both authenticated and anonymous users.

---

## 8. API Endpoints

### Auth Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | None | Email/password registration |
| POST | `/api/auth/login` | None | Email/password login |
| POST | `/api/auth/google` | None | Google OAuth token exchange |

### Scan Endpoint

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/scan/{barcode}` | Optional JWT | Core endpoint — scan a barcode |

**Request:** `POST /api/scan/7622201169016`
**Response (`ProductResponse`):**
```json
{
  "barcode": "7622201169016",
  "name": "Oreo Original",
  "brand": "Mondelez",
  "image_url": "...",
  "verdict": "PASS",
  "verdict_color": "#FF0000",
  "health_score": 22,
  "health_scale": 2.2,
  "is_good_for_health": false,
  "safe_consumption_frequency": "Avoid — consume only on special occasions",
  "health_reason": "High sugar (29g/100g) and ultra-processed (NOVA 4)...",
  "summary": "Oreo is a ultra-processed chocolate sandwich cookie...",
  "ingredients_analysis": [
    { "name": "Sugar", "quantity": "29g/100g", "status": "Bad", "reason": "..." },
    ...
  ],
  "nutrition_analysis": {
    "energy_estimation": "480 kcal/100g — high calorie density...",
    "macronutrient_balance": "High carbs (71g), moderate fat (21g), low protein..."
  },
  "nova_group": 4,
  "nutri_score": "e",
  "nutrients": { "sugars_100g": 29, "fat_100g": 21, ... },
  "additives_tags": ["en:e322i", "en:e500ii", ...],
  "alternatives": []
}
```

### User Profile Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/users/profile` | Required JWT | Get own profile |
| PATCH | `/api/users/profile` | Required JWT | Update profile fields |

### Utility Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/report-missing` | Required JWT | Report a barcode not in OpenFoodFacts |
| GET | `/health` | None | Health check — returns `{"status": "online"}` |

---

## 9. OpenFoodFacts Integration

**File:** [app/services/openfoodfacts.py](app/services/openfoodfacts.py)

OpenFoodFacts (OFF) is a free, open-source food database with 3M+ products worldwide. The API is called with a single barcode:

```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
```

The service:
- Uses `httpx.AsyncClient` — non-blocking, compatible with FastAPI's async runtime
- Sets a 10-second timeout to prevent hanging on slow responses
- Returns `None` if `data["status"] != 1` (product not found)
- Extracts and returns a normalized dict with ~15 fields

**Caching:** Once a product is fetched and stored in PostgreSQL, subsequent scans of the same barcode skip the OpenFoodFacts call entirely. The `scan_count` is incremented instead. This reduces external API dependency and latency for repeat scans.

---

## 10. LangGraph Agentic Workflow

**Files:** [app/agents/workflow.py](app/agents/workflow.py), [app/agents/nodes.py](app/agents/nodes.py), [app/agents/state.py](app/agents/state.py)

LangGraph is used to define a **directed state graph** of agents. Each node receives the full `AgentState` TypedDict and returns a partial update to it.

### Graph Topology

```
[START] → intent_agent → [orchestrator_node] → nutritionist_agent → response_synthesizer → [END]
                                             ↘ response_synthesizer → [END]
                                               (non-food fast path)
```

### AgentState

The shared state that flows through every node:

```python
class AgentState(TypedDict):
    product_name: str
    ingredients_text: str
    is_food: bool                     # Set by intent_agent
    barcode: str
    ingredients: List[str]
    nutrients: Dict[str, Any]
    category_tag: str
    nova_group: Optional[int]
    nutri_score: Optional[str]
    additives_tags: List[str]
    nutrient_levels: Dict[str, Any]
    serving_size: Optional[str]
    scoring_result: Optional[Dict]    # Set by nutritionist_agent Phase 1
    analysis_result: Optional[AIAnalysisResult]  # Set by nutritionist_agent Phase 2
    final_response: Dict[str, Any]   # Set by response_synthesizer
```

### Node 1: `intent_agent`

**Purpose:** Classify whether the scanned product is human-consumable food.

**Why it exists:** OpenFoodFacts contains non-food items (cleaning products, pet food, cosmetics). Without this filter, the nutritionist would try to analyze bleach. Previous implementation was brittle keyword matching — the LLM handles edge cases like protein shakes, baby formula, cooking ingredients, and borderline items (toothpaste, vitamin pills).

**Implementation:**
- Sends product name + category tag to Groq LLM
- Forces `response_format={"type": "json_object"}` for structured output
- Uses `temperature=0` for deterministic classification
- Returns `{"is_food": true/false}`
- **Fails safe**: if the LLM call fails, defaults to `is_food=True` — better to over-analyze than block a real food

### Router: `orchestrator_node`

A pure Python function (no LLM call) that reads `state["is_food"]` and returns a string key:
- `"nutritionist_agent"` → food path
- `"response_synthesizer"` → non-food fast-path (skip scoring, return rejection message)

### Node 2: `nutritionist_agent`

**Two-phase pipeline:**

**Phase 1 — Deterministic Scoring (no LLM):**
Calls `compute_health_score()` from `scoring.py` with all product data. Returns a `ScoringResult` with locked numeric scores (`health_score`, `verdict`, `health_scale`, etc.).

**Phase 2 — LLM Text Generation:**
Passes the locked `ScoringResult` + raw product data to `analyze_product_detailed()`. The LLM generates only text fields (`health_reason`, `summary`, `ingredients_analysis`, `nutrition_analysis`). It cannot touch the numbers.

### Node 3: `response_synthesizer`

Handles 4 cases gracefully:
- **Case A (non-food):** Returns structured rejection payload with `health_score=0`
- **Case B (full success):** Returns `analysis_result.model_dump()` — all fields populated
- **Case C (LLM failed, scoring succeeded):** Returns locked scores from scoring engine with fallback text — scores are NOT zeroed out
- **Case D (everything failed):** Returns minimal error payload

---

## 11. Deterministic Scoring Engine

**File:** [app/services/scoring.py](app/services/scoring.py)

This is the most complex component of the project. It scores food on a **0–100 scale** using 5 pillars plus special overrides.

### Scoring Pillars

| Pillar | Max Points | Source |
|---|---|---|
| 1. Macronutrient Profile | 30 pts + up to +5 bonus | nutrients dict |
| 1a. Micronutrient Bonus | +5 pts | vitamin/mineral data |
| 2. Processing Level (NOVA) | 25 pts | nova_group |
| 3. Additive Safety | 20 pts | additives_tags |
| 4. Official Nutri-Score | 15 pts | nutri_score grade |
| 5. Ingredient Integrity | 10 pts | ingredients text |
| **Total** | **0–105, clamped to 100** | |

### Pillar 1: Macronutrients (0–30 pts)

Category-aware: each food group has its own threshold floors for sugar, salt, sat-fat, trans-fat, and kcal. For example:
- Beverages: sugar floor = 2.5g/100g (strict — drinks add up fast)
- Confectionery: sugar floor = 20g/100g (lenient — chocolate is expected to have sugar)
- Oils: sat-fat floor = 20g/100g (lenient — fats are normal in oil)

9 category groups are detected by keyword matching in the product's category string: `oils`, `beverages`, `dairy`, `protein_foods`, `whole_foods`, `condiments`, `confectionery`, `grains_bakery`, `snacks`.

**Continuous interpolation:** Penalties are not binary. Sugar 10g/100g above the floor is penalized less than sugar 40g/100g above the floor. Uses smooth linear interpolation between known anchor points.

**Smart heuristics:**
- **Natural sugar discount:** If the ingredients text contains no added sugar keywords (e.g., pure orange juice), the sugar penalty is cut by 80%. If NOVA 1 (unprocessed), penalty is halved.
- **Nut/seed grace:** Nuts and seeds naturally have high saturated fat and calories. If the product is detected as a nut/seed (by name or category), the sat-fat penalty is cut 80% and the kcal penalty is waived entirely.
- **Single-ingredient grace:** Single-ingredient natural foods (e.g., pure olive oil, 100% peanut butter) get 50% reduction on sat-fat and kcal penalties.
- **Artificial sweetener penalty:** -10 points applied if artificial sweeteners are detected. Also a hard cap at 65 to prevent "diet soda loophole" — a drink with zero sugar but full of aspartame should not score 85.
- **Serving size scaling:** Deductions are scaled by serving size. A condiment with 5g serving gets reduced penalty compared to a meal with 300g serving.

**Bonuses:**
- Fiber ≥ 8g/100g → +6 pts
- Protein ≥ 25g/100g → +4 pts

### Pillar 2: NOVA Processing (0–25 pts)

| NOVA Group | Score | Description |
|---|---|---|
| 1 | 25 | Unprocessed / minimally processed (apple, egg, milk) |
| 2 | 20 | Processed culinary ingredients (flour, oil, salt) |
| 3 | 12 | Processed food (canned fish, cheese, cured meat) |
| 4 | 0 | Ultra-processed food (soft drinks, chips, instant noodles) |

If NOVA group is unknown, it's estimated from the additive count.

### Pillar 3: Additive Safety (starts at 20, deductions only)

Each additive tag is classified:
- **Red (high-risk) -5 pts each:** Azo dyes (E102, E110, E122, E124, E129 — ADHD linked), sodium benzoate (E211), nitrites (E249-E252), MSG (E621), aspartame (E951), cyclamate (E952, banned in USA), saccharin (E954)
- **Orange (moderate-risk) -3 pts each:** Caramel colorings (4-MEI), BHA/BHT (E320/321), carrageenan (E407), mono/diglycerides (E471), sucralose (E955)
- **Green (safe/beneficial) 0 pts:** Vitamin C (E300), tocopherols (E306-309), citric acid (E330), lactic acid (E270)
- **Unknown -1 pt each:** Any other E-number

Small-serving condiments (serving < 15g) get reduced additive penalties, since the absolute dose is small.

### Pillar 4: Official Nutri-Score (0–15 pts)

| Grade | Points |
|---|---|
| A | 15 |
| B | 12 |
| C | 9 |
| D | 5 |
| E | 2 |
| Not available | 7 (neutral) |

### Pillar 5: Ingredient Integrity (0–10 pts)

Deductions for:
- **High ingredient count:** > 30 ingredients → -4 pts (complexity = processing)
- **Red flag ingredients:** HFCS, partially hydrogenated fats, trans fat → up to -3 pts each
- **Inflammatory oils:** Palm oil → -4 pts; refined vegetable oils → -2 pts
- **Glycemic fillers:** Maltodextrin → -2 pts; modified starch → -1.5 pts
- **Artificial sweeteners** (integrity penalty, separate from Pillar 1): -3 pts
- **Sugar listed first** in ingredients (by weight) → -2 pts

### Special Hard Caps

- **Alcohol:** If `alcohol_100g > 0.5`, score is hard-capped at 40. Alcohol products cannot score above 40 regardless of other factors.
- **Artificial sweeteners:** Score hard-capped at 65. Prevents misleadingly high scores for diet products.

### Verdict and Derived Fields

- `verdict`: `"SMASH"` if `health_score >= 60`, else `"PASS"`
- `health_scale`: `health_score / 10.0` (e.g., 72 → 7.2 out of 10)
- `safe_consumption_frequency`: Lookup table:
  - ≥ 80 → Daily
  - ≥ 65 → 3–4 times per week
  - ≥ 50 → Weekly
  - ≥ 35 → Rarely (once or twice a month)
  - ≥ 20 → Avoid — consume only on special occasions
  - < 20 → Never recommended

### Data Confidence Score

A float 0.0–1.0 indicating how much data was available to compute the score:
- Complete nutrient data: +0.30
- NOVA group known: +0.25
- Additive list present: +0.20
- Nutri-Score present: +0.15
- Ingredients text present: +0.10

Passed to the LLM so it can calibrate how confident to sound in its explanation.

---

## 12. LLM Text Generation (Groq)

**File:** [app/agents/nutritionist.py](app/agents/nutritionist.py)

### Why Groq?

Groq runs LLaMA 3.3 70B at extremely high speed (tokens per second ~200–400). This is critical for a scan flow — the user is waiting in real time with their phone pointed at a product. Groq's latency is typically under 2 seconds even for complex prompts.

### Prompt Engineering Strategy

The LLM receives:
1. **Locked numeric scores** — explicitly told it CANNOT change these
2. **Score breakdown** — the pillar scores, deductions, and bonuses in JSON so it can reference real reasons
3. **Raw product data** — for ingredient-level analysis
4. **Data confidence label** — so the LLM knows whether to speak confidently or cautiously

The LLM is asked to generate exactly 4 fields:
- `health_reason`: 1-2 sentences explaining WHY the product scored the way it did, referencing real deductions by name
- `summary`: 2 sentences — what the product is + practical consumer takeaway
- `ingredients_analysis`: Per-ingredient assessment (name, quantity, Good/Bad/Neutral, reason)
- `nutrition_analysis`: Energy estimation + macronutrient balance

`response_format={"type": "json_object"}` forces structured JSON output — no markdown, no preamble, just parseable JSON.

### Merge Strategy

After the LLM responds:
```python
return AIAnalysisResult(
    # Locked scores from scoring engine (LLM cannot override)
    verdict=scoring.verdict,
    health_score=scoring.health_score,
    health_scale=scoring.health_scale,
    is_good_for_health=scoring.is_good_for_health,
    safe_consumption_frequency=scoring.safe_consumption_frequency,
    # Text from LLM
    health_reason=llm_output.get("health_reason", "Analysis unavailable."),
    summary=llm_output.get("summary", "Analysis unavailable."),
    ingredients_analysis=llm_output.get("ingredients_analysis", []),
    nutrition_analysis=llm_output.get("nutrition_analysis", {...}),
)
```

The numeric fields are always taken from the scoring engine. The LLM can only contribute text.

---

## 13. Personalization System

When a user is authenticated and has a profile, the scan endpoint loads their `UserProfile` and injects it into the agent state:

```python
"user_profile": {
    "dietary_preferences": profile.dietary_preferences,  # e.g., "vegan"
    "health_tags": profile.health_tags,                 # e.g., ["Diabetic"]
    "allergies": profile.allergies,                     # e.g., ["peanut", "gluten"]
    "health_goals": profile.health_goals                # e.g., "weight loss"
}
```

The LLM nutritionist has access to this context when generating explanations. For example:
- A diabetic user scanning a high-sugar product will get a more urgent warning
- A vegan user scanning a product with animal-derived additives will be flagged
- A user with a peanut allergy scanning a product with peanuts will get an allergen warning

The scoring engine itself currently does not change scores based on profile (scores are objective per 100g data), but the LLM explanation is personalized.

---

## 14. Deployment (Docker + Render)

### Dockerfile

```dockerfile
FROM python:3.11-slim

ENV PYTHONUNBUFFERED True
WORKDIR /app

# Build essentials for psycopg binary
RUN apt-get update && apt-get install -y build-essential libpq-dev

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . ./

EXPOSE 8000
CMD uvicorn app.main:app --host 0.0.0.0 --port ${PORT:=8000}
```

Key points:
- `python:3.11-slim` — minimal image, no unnecessary packages
- `build-essential` + `libpq-dev` — required to build psycopg (PostgreSQL C driver)
- `PYTHONUNBUFFERED=True` — logs appear immediately in Render's dashboard
- `${PORT:=8000}` — Render injects a dynamic `$PORT` env var; this falls back to 8000 locally

### Database: Neon Serverless PostgreSQL

Neon is a serverless PostgreSQL provider with:
- Instant provisioning (no server to configure)
- Scales to zero when idle (free tier)
- Standard PostgreSQL wire protocol — works with any psycopg/SQLAlchemy driver

The `DATABASE_URL` env var is set on Render and automatically transformed from `postgres://` to `postgresql+psycopg://` by the config validator.

### `init_db()` — Auto-Migration

On every startup, `init_db()` runs:
1. `SQLModel.metadata.create_all` — creates all tables that don't exist yet (idempotent)
2. A series of `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` and `ALTER COLUMN ... DROP NOT NULL` statements — applies schema changes without needing Alembic migrations for simple changes

### Database Connection Pool

```python
engine = create_async_engine(
    settings.DATABASE_URL,
    echo=False,
    future=True,
    pool_pre_ping=True,   # Test connection health before using
    pool_recycle=3600     # Recycle connections every hour (prevents stale connections)
)
```

---

## 15. Key Design Decisions

### Why LangGraph instead of a simple sequential function call?

LangGraph makes the workflow **explicit, visual, and extensible**:
- The graph topology is defined once and easy to reason about
- Adding a new agent (e.g., an allergen-checker agent) is a matter of adding a node and an edge
- Conditional routing (`orchestrator_node`) is a first-class concept — no nested if/else
- LangGraph's `ainvoke` is async-native — all nodes can run in parallel if needed
- In production, LangGraph supports persistence, streaming, and human-in-the-loop checkpoints

### Why separate scoring from LLM?

Health scores must be **reproducible and auditable**. If an LLM computed the score:
- The same product could score differently on two requests
- It's impossible to explain why a score changed
- Users could not trust the app

With deterministic scoring, the same barcode always produces the same score. The LLM only generates prose — which is fine to be slightly different each time.

### Why cache products in PostgreSQL instead of calling OpenFoodFacts every time?

- Latency: OpenFoodFacts can be slow (1-3 seconds). Cache eliminates this for repeat scans.
- Reliability: If OpenFoodFacts is down, cached products still work
- Rate limits: OpenFoodFacts has no official rate limits but is community-run — caching is good practice

### Why SQLModel over pure SQLAlchemy or Tortoise ORM?

SQLModel uses the same Pydantic model class for both the database table definition and API request/response validation. This eliminates duplication — one class does both. It's built by the FastAPI author (Sebastián Ramírez) and integrates naturally.

### Why Groq over OpenAI?

For a scan endpoint that a user is waiting on in real time, latency is the primary constraint. Groq's LPU (Language Processing Unit) hardware runs LLaMA 3.3 70B at 200-400 tokens/second — roughly 5-10x faster than OpenAI's API for the same model size. Free tier is also generous for development.

---

## 16. Interview Q&A Preparation

**Q: Walk me through what happens when I scan a barcode.**

A: The mobile app sends `POST /api/scan/7622201169016`. The backend optionally validates a JWT to identify the user. It checks the PostgreSQL cache — if the barcode was scanned before, the product data is loaded from the database. If not, it calls the OpenFoodFacts API via httpx to fetch product data (name, brand, ingredients, nutrients, NOVA group, Nutri-Score, additives, etc.) and saves it. Then it runs a LangGraph 3-node graph: first, an intent agent uses the LLaMA LLM to confirm it's actually food; then a nutritionist agent runs a 5-pillar deterministic scoring engine (pure Python, no LLM) to compute a 0-100 health score, then the same LLM generates a human-readable explanation of that score. A response synthesizer assembles the final payload and the API returns it.

**Q: How do you prevent the LLM from hallucinating health scores?**

A: I split scoring and explanation into two completely separate systems. The scoring engine is pure Python with hard-coded nutritional thresholds — it always produces the same score for the same input data. The LLM only sees the finished scores and is instructed to explain them, not compute them. The final `AIAnalysisResult` merges locked numeric fields from the scoring engine with text fields from the LLM. The LLM cannot override the numbers.

**Q: What is NOVA and why does it matter?**

A: NOVA is a food classification system developed by Brazilian researchers (Monteiro et al.) that classifies foods by their degree of industrial processing, not their nutritional content. NOVA 1 is unprocessed (fresh apple, egg, milk). NOVA 4 is ultra-processed (soft drinks, instant noodles, packaged snacks). Epidemiological studies link high NOVA 4 consumption to obesity, diabetes, and cardiovascular disease even when the macros look similar to less-processed foods. OpenFoodFacts provides NOVA groups for many products.

**Q: How does the personalization work?**

A: When a user registers, a `UserProfile` row is automatically created with a 1:1 relationship. The profile stores dietary preferences, health goals, allergies, and health tags. On every scan, if the user is authenticated, the profile is loaded and injected into the agent state. The LLM nutritionist receives this context and adjusts its explanation — warning a diabetic about sugar, flagging allergens, or noting vegan/keto compatibility. The numeric score is currently objective (per 100g data), but the text explanation is personalized.

**Q: How does authentication work for both email and Google login?**

A: Both methods produce the same backend JWT, so from the API's perspective downstream endpoints don't care how you authenticated. For email, we bcrypt-hash the password at registration, verify it at login, and issue a JWT. For Google, the mobile app completes the OAuth flow natively (with Google's SDK), gets a Google `id_token`, and sends it to our backend. We verify it server-side using `google-auth` library — checking the signature, expiry, and audience claim (our Google Client ID). If valid, we extract the Google user ID and email, find or create the user, and issue our own JWT. After that, the user is like any other JWT holder.

**Q: What is a LangGraph StateGraph and why use it?**

A: LangGraph's `StateGraph` is a directed acyclic graph (or DAG with conditional branches) where nodes are Python async functions and edges define execution order. The state is a shared TypedDict that flows through every node — each node receives the full state and returns a partial update. The `orchestrator_node` is a conditional edge router that returns a string key to select the next node. Using a graph makes the workflow topology explicit and auditable, supports async execution, and makes it easy to add new agents by inserting nodes.

**Q: Why not store the AI analysis in the database?**

A: Analysis is currently re-run on every scan because: (1) it's personalized per user — the same product should produce different explanations for a diabetic vs. a gym-goer; (2) the scoring parameters might change as we tune the engine; (3) Groq is fast enough that re-running is cheap. The product data (ingredients, nutrients, NOVA, etc.) is cached in PostgreSQL, which is the expensive external API call. The analysis is the cheap computation.

**Q: What would you improve if you had more time?**

A: Several things: (1) proper structured logging instead of `print()` statements — use Python's `logging` module with log levels; (2) barcode format validation before querying OpenFoodFacts; (3) the `alternatives` field in `ProductResponse` is always empty — it's intended to suggest healthier alternatives but was never implemented; (4) CORS is `allow_origins=["*"]` which is only fine for development — should be restricted to the specific mobile app domain; (5) actual Alembic migrations instead of `ALTER TABLE IF NOT EXISTS` hacks in `init_db()`; (6) scoring personalization — the numeric score itself should adjust based on user health profile, not just the text explanation.

**Q: How would you scale this if it got 100x more traffic?**

A: The current stack is already mostly async (FastAPI + psycopg async + httpx), so it handles concurrent requests efficiently. For heavier load: (1) add Redis caching in front of PostgreSQL for the hottest barcodes; (2) put the LangGraph workflow on a task queue (Celery or ARQ) so the HTTP response returns a job ID and the client polls for results; (3) scale Render horizontally with multiple containers; (4) Neon's serverless PostgreSQL autoscales read replicas. The OpenFoodFacts call and Groq call are the main latency bottlenecks.

---

*Documentation written for interview preparation. All code is in the `app/` directory.*
