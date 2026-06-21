from pydantic import BaseModel, Field
from typing import Any


class ApiResult(BaseModel):
    code: int = 200
    message: str = "success"
    data: Any = None

    @staticmethod
    def success(data: Any = None, message: str = "success") -> dict:
        return {"code": 200, "message": message, "data": data}

    @staticmethod
    def error(code: int = 500, message: str = "error") -> dict:
        return {"code": code, "message": message, "data": None}


class PageResult(BaseModel):
    total: int = 0
    records: list = Field(default_factory=list)
    size: int = 10
    page: int = 1
