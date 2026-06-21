from datetime import datetime
from sqlalchemy import String, Text, Integer, BigInteger, DateTime, func, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base


class Task(Base):
    __tablename__ = "task"
    __table_args__ = (
        Index("ix_task_paper_id", "paper_id"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    paper_id: Mapped[int | None] = mapped_column(BigInteger)
    agent_role: Mapped[str] = mapped_column(String(30), nullable=False)
    sort_order: Mapped[int] = mapped_column(Integer, default=0)
    version_no: Mapped[int] = mapped_column(Integer, default=0)
    description: Mapped[str | None] = mapped_column(String(500))
    input_data: Mapped[str | None] = mapped_column(Text)
    output_data: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="PENDING")
    duration_ms: Mapped[int | None] = mapped_column(BigInteger)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), nullable=False
    )
    completed_at: Mapped[datetime | None] = mapped_column(DateTime)
