"""
Two-way sync for meal logs and weight entries.

The app sends rows it changed since its last sync and the server time it got back last time;
the server stores them and returns every row of this user changed after that time. All
timestamps are epoch milliseconds (UTC) to avoid time-zone parsing on the phone.
"""
from datetime import datetime, timezone
from typing import List, Optional

from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlmodel import select

from app.api.deps import get_current_user
from app.core.database import get_session
from app.core.limiter import limiter
from app.models.tracking import MealEntry, WeightEntry
from app.models.users import User

router = APIRouter()

MAX_ROWS_PER_SYNC = 2000


def _to_dt(ms: int) -> datetime:
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc).replace(tzinfo=None)


def _to_ms(dt: datetime) -> int:
    return int(dt.replace(tzinfo=timezone.utc).timestamp() * 1000)


class MealSync(BaseModel):
    client_id: str = Field(min_length=1, max_length=64)
    logged_at_ms: int
    meal_slot: str = Field(default="snack", max_length=20)
    food_id: str = Field(default="", max_length=128)
    food_name: str = Field(default="", max_length=200)
    quantity: float = 0
    unit: str = Field(default="g", max_length=16)
    calories: float = 0
    protein: float = 0
    carbs: float = 0
    fat: float = 0
    fiber: float = 0
    iron: float = 0
    calcium: float = 0
    zinc: float = 0
    b12: float = 0
    vitamin_d: float = 0
    folate: float = 0
    is_estimated: bool = False
    deleted: bool = False


class WeightSync(BaseModel):
    client_id: str = Field(min_length=1, max_length=64)
    measured_at_ms: int
    weight_kg: float = Field(gt=0, lt=500)
    deleted: bool = False


class SyncRequest(BaseModel):
    since_ms: Optional[int] = None
    meals: List[MealSync] = []
    weights: List[WeightSync] = []


class SyncResponse(BaseModel):
    server_time_ms: int
    meals: List[MealSync]
    weights: List[WeightSync]


_MEAL_FIELDS = ["meal_slot", "food_id", "food_name", "quantity", "unit", "calories", "protein", "carbs",
                "fat", "fiber", "iron", "calcium", "zinc", "b12", "vitamin_d", "folate", "is_estimated", "deleted"]


@router.post("/sync", response_model=SyncResponse)
@limiter.limit("30/minute")
async def sync_tracking(
    request: Request,
    data: SyncRequest,
    session: AsyncSession = Depends(get_session),
    current_user: User = Depends(get_current_user),
):
    uid = current_user.id
    # Read the clock before writing so rows saved in this call are returned to the caller too;
    # the next sync starts from here and nothing written concurrently is skipped.
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    meals_in = data.meals[:MAX_ROWS_PER_SYNC]
    if meals_in:
        ids = [m.client_id for m in meals_in]
        existing = {
            row.client_id: row for row in (await session.execute(
                select(MealEntry).where(MealEntry.user_id == uid, MealEntry.client_id.in_(ids))
            )).scalars()
        }
        for m in meals_in:
            row = existing.get(m.client_id) or MealEntry(user_id=uid, client_id=m.client_id, logged_at=_to_dt(m.logged_at_ms))
            row.logged_at = _to_dt(m.logged_at_ms)
            for f in _MEAL_FIELDS:
                setattr(row, f, getattr(m, f))
            row.updated_at = now
            session.add(row)
            existing[m.client_id] = row

    weights_in = data.weights[:MAX_ROWS_PER_SYNC]
    if weights_in:
        ids = [w.client_id for w in weights_in]
        existing_w = {
            row.client_id: row for row in (await session.execute(
                select(WeightEntry).where(WeightEntry.user_id == uid, WeightEntry.client_id.in_(ids))
            )).scalars()
        }
        for w in weights_in:
            row = existing_w.get(w.client_id) or WeightEntry(
                user_id=uid, client_id=w.client_id, measured_at=_to_dt(w.measured_at_ms), weight_kg=w.weight_kg)
            row.measured_at = _to_dt(w.measured_at_ms)
            row.weight_kg = w.weight_kg
            row.deleted = w.deleted
            row.updated_at = now
            session.add(row)
            existing_w[w.client_id] = row

    await session.commit()

    meal_q = select(MealEntry).where(MealEntry.user_id == uid)
    weight_q = select(WeightEntry).where(WeightEntry.user_id == uid)
    if data.since_ms is not None:
        since = _to_dt(data.since_ms)
        meal_q = meal_q.where(MealEntry.updated_at >= since)
        weight_q = weight_q.where(WeightEntry.updated_at >= since)
    meals_out = (await session.execute(meal_q.order_by(MealEntry.logged_at.desc()).limit(5000))).scalars().all()
    weights_out = (await session.execute(weight_q.order_by(WeightEntry.measured_at.desc()).limit(2000))).scalars().all()

    return SyncResponse(
        server_time_ms=_to_ms(now),
        meals=[MealSync(client_id=m.client_id, logged_at_ms=_to_ms(m.logged_at),
                        **{f: getattr(m, f) for f in _MEAL_FIELDS}) for m in meals_out],
        weights=[WeightSync(client_id=w.client_id, measured_at_ms=_to_ms(w.measured_at),
                            weight_kg=w.weight_kg, deleted=w.deleted) for w in weights_out],
    )
