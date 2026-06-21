from pydantic import BaseModel
from datetime import datetime


class PaperWritingRequest(BaseModel):
    topic: str
    description: str | None = None
    keywords: str | None = None
    sections: list[str] | None = None
    requirements: str | None = None
    maxReviewRounds: int = 3
    flowId: str | None = "standard"
    kgId: int | None = None


class SectionVO(BaseModel):
    title: str
    length: int


class StepRecordVO(BaseModel):
    agentName: str
    agentRole: str
    status: str
    durationMs: int = 0
    summary: str | None = None
    fullOutput: str | None = None


class PaperWritingVO(BaseModel):
    contextId: str | None = None
    paperId: int | None = None
    topic: str | None = None
    finalDraft: str | None = None
    abstractText: str | None = None
    sections: list[SectionVO] = []
    reviewComments: list[str] = []
    steps: list[StepRecordVO] = []
    status: str = "PARTIAL"
    totalDurationMs: int = 0


class ResearchRequest(BaseModel):
    topic: str
    description: str | None = None
    keywords: str | None = None
    requirements: str | None = None
