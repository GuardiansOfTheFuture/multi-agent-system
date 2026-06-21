from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # Database
    database_url: str = "mysql+aiomysql://root:123456@localhost:3306/paper_ai?charset=utf8mb4"

    # Redis
    redis_url: str = "redis://localhost:6379/0"

    # JWT
    jwt_secret: str = "PaperAI-Default-Secret-Key-2026-For-Dev"
    jwt_expire_hours: int = 24

    # MiMo API
    mimo_api_key: str = ""
    mimo_base_url: str = "https://token-plan-cn.xiaomimimo.com/v1"
    mimo_model: str = "mimo-v2.5-pro"
    mimo_light_model: str = "mimo-v2.5-pro"

    # Celery
    celery_broker_url: str = "amqp://guest:guest@localhost:5672//"
    celery_result_backend: str = "redis://localhost:6379/1"

    # RAG
    rag_vector_top_k: int = 20
    rag_keyword_top_k: int = 10
    rag_similarity_threshold: float = 0.5
    rag_final_top_k: int = 5

    # Cache
    cache_default_ttl: int = 600
    cache_llm_enabled: bool = True
    cache_llm_ttl: int = 1800

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        extra = "ignore"


@lru_cache
def get_settings() -> Settings:
    return Settings()
