from pydantic import BaseModel, model_validator
from datetime import datetime


class LoginRequest(BaseModel):
    username: str
    password: str


class RegisterRequest(BaseModel):
    username: str
    password: str
    email: str | None = None


class UserVO(BaseModel):
    id: int
    username: str
    email: str | None = None
    avatar: str | None = None
    createdAt: datetime | None = None

    class Config:
        from_attributes = True

    @model_validator(mode="before")
    @classmethod
    def map_fields(cls, data):
        if hasattr(data, "created_at"):
            # SQLAlchemy 对象：将 created_at 映射到 createdAt
            return {
                "id": data.id,
                "username": data.username,
                "email": data.email,
                "avatar": data.avatar,
                "createdAt": data.created_at,
            }
        if isinstance(data, dict) and "created_at" in data and "createdAt" not in data:
            data["createdAt"] = data.pop("created_at")
        return data


class LoginVO(BaseModel):
    token: str
    user: UserVO
