from datetime import datetime
from sqlalchemy import String, Integer, BigInteger, DateTime, func, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base


class KnowledgeDocument(Base):
    __tablename__ = "knowledge_document"
    __table_args__ = (
        Index("ix_kd_user_id", "user_id"),
        Index("ix_kd_scope", "scope"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    filename: Mapped[str] = mapped_column(String(300), nullable=False)
    file_type: Mapped[str] = mapped_column(String(20), nullable=False)
    title: Mapped[str | None] = mapped_column(String(500))
    authors: Mapped[str | None] = mapped_column(String(500))
    year: Mapped[int | None] = mapped_column(Integer)
    scope: Mapped[str] = mapped_column(String(10), nullable=False, default="PRIVATE")
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="COMPLETED")
    total_chunks: Mapped[int] = mapped_column(Integer, default=0)
    total_chars: Mapped[int] = mapped_column(Integer, default=0)
    store_path: Mapped[str | None] = mapped_column(String(500))
    embed_dim: Mapped[int | None] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), nullable=False
    )
