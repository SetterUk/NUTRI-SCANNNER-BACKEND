"""
Per-user diet tracking synced from the app (meal logs and weight). The phone is the source of
truth while offline; each row carries a client-generated id so the same entry is never duplicated,
and deletions are soft so they sync to the user's other installs.
"""
from datetime import datetime
from typing import Optional

from sqlalchemy import UniqueConstraint
from sqlmodel import SQLModel, Field


class MealEntry(SQLModel, table=True):
    __table_args__ = (UniqueConstraint("user_id", "client_id", name="uq_meal_user_client"),)

    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    client_id: str = Field(max_length=64)

    logged_at: datetime
    meal_slot: str = Field(max_length=20)
    food_id: str = Field(max_length=128)
    food_name: str = Field(max_length=200)
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
    updated_at: datetime = Field(default_factory=datetime.utcnow, index=True)


class WeightEntry(SQLModel, table=True):
    __table_args__ = (UniqueConstraint("user_id", "client_id", name="uq_weight_user_client"),)

    id: Optional[int] = Field(default=None, primary_key=True)
    user_id: int = Field(foreign_key="user.id", index=True)
    client_id: str = Field(max_length=64)

    measured_at: datetime
    weight_kg: float

    deleted: bool = False
    updated_at: datetime = Field(default_factory=datetime.utcnow, index=True)
