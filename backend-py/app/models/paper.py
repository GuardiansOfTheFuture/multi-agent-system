from sqlalchemy import String, Text, Integer, BigInteger
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base, TimestampMixin


from sqlalchemy import Index

class Paper(Base, TimestampMixin):
    __tablename__ = "paper"
    __table_args__ = (
        Index("ix_paper_user_id", "user_id"),
        Index("ix_paper_status", "status"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    title: Mapped[str] = mapped_column(String(300), nullable=False)
    abstract_text: Mapped[str | None] = mapped_column(Text)
    keywords: Mapped[str | None] = mapped_column(String(500))
    description: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="DRAFT")
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    current_version: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    kg_id: Mapped[int | None] = mapped_column(BigInteger)
