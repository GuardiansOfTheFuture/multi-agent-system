from sqlalchemy import String, Text, Integer, BigInteger, Float, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base, TimestampMixin


class CustomAgent(Base, TimestampMixin):
    __tablename__ = "custom_agent"
    __table_args__ = (
        Index("ix_ca_user_id", "user_id"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    icon: Mapped[str] = mapped_column(String(10), default="🤖")
    description: Mapped[str | None] = mapped_column(String(500))
    system_prompt: Mapped[str | None] = mapped_column(Text)
    model: Mapped[str] = mapped_column(String(100), nullable=False, default="mimo-v2.5-pro")
    temperature: Mapped[float] = mapped_column(Float, nullable=False, default=0.7)
    enabled: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
