from datetime import datetime
from sqlalchemy import String, Text, BigInteger, DateTime, func, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base


class Article(Base):
    """论文文档 — 一个对话对应一篇文章"""
    __tablename__ = "article"
    __table_args__ = (
        Index("ix_article_conv_id", "conversation_id"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    conversation_id: Mapped[int] = mapped_column(BigInteger, nullable=False, unique=True)
    title: Mapped[str] = mapped_column(String(300), nullable=False, default="未命名文档")
    content: Mapped[str] = mapped_column(Text, nullable=True, default="")
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), onupdate=func.now(), nullable=False
    )
