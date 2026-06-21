import logging
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from ..config import get_settings

log = logging.getLogger("paperai.db")

settings = get_settings()
engine = create_async_engine(settings.database_url, echo=False, pool_pre_ping=True)
async_session = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
log.info("数据库连接: %s", settings.database_url.split("@")[-1] if "@" in settings.database_url else settings.database_url)


async def get_db() -> AsyncSession:
    async with async_session() as session:
        yield session
