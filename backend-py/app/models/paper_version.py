from datetime import datetime
from sqlalchemy import String, Text, Integer, BigInteger, DateTime, func, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base


class PaperVersion(Base):
    __tablename__ = "paper_version"
    __table_args__ = (
        Index("ix_paper_version_paper_id", "paper_id"),
        Index("ix_paper_version_no", "paper_id", "version_no"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    paper_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    version_no: Mapped[int] = mapped_column(Integer, nullable=False)
    stage: Mapped[str] = mapped_column(String(20), nullable=False, default="DRAFT")
    summary: Mapped[str | None] = mapped_column(String(500))
    content: Mapped[str | None] = mapped_column(Text)
    word_count: Mapped[int] = mapped_column(Integer, default=0)
    edit_type: Mapped[str] = mapped_column(String(20), default="MANUAL")
    change_summary: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), nullable=False
    )
