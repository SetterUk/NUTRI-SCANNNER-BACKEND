"""Meal/weight sync against an in-memory SQLite database (no Postgres, no network)."""
import asyncio

import httpx
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from sqlmodel import SQLModel

from app.api.deps import get_current_user
from app.core.database import get_session
from app.main import app
from app.models.users import User


def run(scenario):
    async def main():
        engine = create_async_engine("sqlite+aiosqlite://", poolclass=StaticPool)
        async with engine.begin() as conn:
            await conn.run_sync(SQLModel.metadata.create_all)
        factory = sessionmaker(bind=engine, class_=AsyncSession, expire_on_commit=False)
        async with factory() as s:
            s.add(User(id=1, email="a@example.com"))
            s.add(User(id=2, email="b@example.com"))
            await s.commit()

        async def session_override():
            async with factory() as s:
                yield s

        current = {"user": User(id=1, email="a@example.com")}
        app.dependency_overrides[get_session] = session_override
        app.dependency_overrides[get_current_user] = lambda: current["user"]
        try:
            transport = httpx.ASGITransport(app=app)
            async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
                await scenario(client, current)
        finally:
            app.dependency_overrides.clear()
            await engine.dispose()

    asyncio.run(main())


def meal(cid, kcal=300.0, deleted=False, ms=1_760_000_000_000):
    return {"client_id": cid, "logged_at_ms": ms, "meal_slot": "lunch", "food_id": "dal",
            "food_name": "Moong dal", "quantity": 150, "calories": kcal, "protein": 9, "deleted": deleted}


def test_push_then_pull_on_a_new_install():
    async def scenario(client, current):
        r = await client.post("/api/tracking/sync", json={"meals": [meal("m1"), meal("m2")],
                                                           "weights": [{"client_id": "w1", "measured_at_ms": 1_760_000_000_000, "weight_kg": 72.5}]})
        assert r.status_code == 200, r.text
        body = r.json()
        assert {m["client_id"] for m in body["meals"]} == {"m1", "m2"}
        assert body["weights"][0]["weight_kg"] == 72.5
        assert body["meals"][0]["logged_at_ms"] == 1_760_000_000_000

        # Reinstall: no since, nothing local -> everything comes back
        r = await client.post("/api/tracking/sync", json={})
        assert len(r.json()["meals"]) == 2

    run(scenario)


def test_edits_and_deletes_sync_without_duplicates():
    async def scenario(client, current):
        first = (await client.post("/api/tracking/sync", json={"meals": [meal("m1")]})).json()
        since = first["server_time_ms"]
        r = await client.post("/api/tracking/sync", json={"since_ms": since, "meals": [meal("m1", kcal=450, deleted=True)]})
        meals = r.json()["meals"]
        assert len(meals) == 1 and meals[0]["deleted"] is True and meals[0]["calories"] == 450

        all_rows = (await client.post("/api/tracking/sync", json={})).json()["meals"]
        assert len(all_rows) == 1

    run(scenario)


def test_users_never_see_each_others_logs():
    async def scenario(client, current):
        await client.post("/api/tracking/sync", json={"meals": [meal("same-id")]})
        current["user"] = User(id=2, email="b@example.com")
        r = await client.post("/api/tracking/sync", json={})
        assert r.json()["meals"] == []
        # The same client id from another account is a separate row
        await client.post("/api/tracking/sync", json={"meals": [meal("same-id", kcal=999)]})
        current["user"] = User(id=1, email="a@example.com")
        mine = (await client.post("/api/tracking/sync", json={})).json()["meals"]
        assert [m["calories"] for m in mine] == [300.0]

    run(scenario)


def test_rejects_impossible_weight():
    async def scenario(client, current):
        r = await client.post("/api/tracking/sync", json={"weights": [{"client_id": "w", "measured_at_ms": 1, "weight_kg": 0}]})
        assert r.status_code == 422

    run(scenario)
