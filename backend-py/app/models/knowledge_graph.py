from sqlalchemy import String, Text, BigInteger, JSON, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base, TimestampMixin


class KnowledgeGraph(Base, TimestampMixin):
    __tablename__ = "knowledge_graph"
    __table_args__ = (
        Index("ix_kg_user_id", "user_id"),
        Index("ix_kg_paper_id", "paper_id"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str | None] = mapped_column(String(500))
    paper_id: Mapped[int | None] = mapped_column(BigInteger)
    graph_data: Mapped[dict] = mapped_column(JSON, nullable=False)
