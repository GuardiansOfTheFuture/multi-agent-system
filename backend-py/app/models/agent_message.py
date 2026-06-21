from datetime import datetime
from sqlalchemy import String, Text, BigInteger, DateTime, func
from sqlalchemy.orm import Mapped, mapped_column
from .base import Base


class AgentMessage(Base):
    __tablename__ = "agent_message"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    paper_id: Mapped[int | None] = mapped_column(BigInteger)
    task_id: Mapped[int | None] = mapped_column(BigInteger)
    sender_role: Mapped[str] = mapped_column(String(30), nullable=False)
    receiver_role: Mapped[str | None] = mapped_column(String(30))
    message_type: Mapped[str] = mapped_column(String(30), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), nullable=False
    )
