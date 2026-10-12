"""Scan pipeline with the LLM stubbed out (no network)."""
import asyncio

from app.agents import nutritionist
from app.agents.workflow import nutrition_app_workflow


def _state(profile=None):
    text = "Potato, palmolein oil, sugar, salt, peanuts"
    return {
        "barcode": "123", "product_name": "Aloo Bhujia", "ingredients": text.split(","),
        "ingredients_text": text, "category_tag": "en:snacks,en:salty-snacks", "food_groups": [],
        "off_allergens": "en:peanuts", "is_food": True, "analysis_result": None, "final_response": {},
        "nova_group": 4, "nutri_score": "e", "additives_tags": [], "nutrient_levels": {},
        "serving_size": "30g",
        "nutrients": {"energy-kcal_100g": 560, "fat_100g": 38, "saturated-fat_100g": 15,
                      "sugars_100g": 3, "salt_100g": 2.4, "proteins_100g": 11},
        "scoring_result": None, "user_profile": profile,
    }


class _FailingCompletions:
    async def create(self, **kwargs):
        raise RuntimeError("groq down")


class _CapturingCompletions:
    def __init__(self):
        self.prompts = []

    async def create(self, **kwargs):
        self.prompts.append(kwargs["messages"][-1]["content"])
        content = ('{"health_reason": "Fried and salty.", "summary": "A namkeen.", '
                   '"ingredients_analysis": [{"name": "Potato", "status": "neutral", "reason": "Base."}, '
                   '{"name": "", "status": "Good"}], '
                   '"nutrition_analysis": {"energy_estimation": "560 kcal", "macronutrient_balance": "Fatty"}}')
        msg = type("M", (), {"content": content})
        choice = type("C", (), {"message": msg})
        return type("R", (), {"choices": [choice]})


def _client(completions):
    chat = type("Chat", (), {"completions": completions})
    return type("Client", (), {"chat": chat})


def test_llm_failure_still_gives_full_plain_explanation(monkeypatch):
    monkeypatch.setattr(nutritionist.groq_manager, "get_best_client", lambda: _client(_FailingCompletions()))
    out = asyncio.run(nutrition_app_workflow.ainvoke(_state()))["final_response"]
    assert out["verdict"] in {"GREAT", "GOOD", "OKAY", "LIMIT", "AVOID"}
    assert "unavailable" not in (out["health_reason"] + out["summary"]).lower()
    assert out["score_breakdown"]["concerns"]
    assert "salt" in " ".join(out["score_breakdown"]["concerns"]).lower()


def test_profile_reaches_the_llm_and_bad_rows_are_dropped(monkeypatch):
    completions = _CapturingCompletions()
    monkeypatch.setattr(nutritionist.groq_manager, "get_best_client", lambda: _client(completions))
    out = asyncio.run(nutrition_app_workflow.ainvoke(_state({"health_tags": ["Hypertension"]})))["final_response"]
    assert "Hypertension" in completions.prompts[0]
    assert [i["name"] for i in out["ingredients_analysis"]] == ["Potato"]
    assert out["ingredients_analysis"][0]["status"] == "Neutral"
