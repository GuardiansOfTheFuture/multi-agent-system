from sqlalchemy import String, Text, Integer, BigInteger, JSON, Index
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base, TimestampMixin


class FlowDefinition(Base, TimestampMixin):
    __tablename__ = "flow_definition"
    __table_args__ = (
        Index("ix_fd_user_id", "user_id"),
    )

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str | None] = mapped_column(String(500))
    category: Mapped[str] = mapped_column(String(50), nullable=False, default="custom")
    graph_data: Mapped[dict] = mapped_column(JSON, nullable=False)
    is_template: Mapped[int] = mapped_column(Integer, default=0)
