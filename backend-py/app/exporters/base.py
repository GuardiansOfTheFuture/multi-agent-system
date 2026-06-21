from abc import ABC, abstractmethod


class Exporter(ABC):
    @abstractmethod
    def export(self, markdown: str, title: str) -> bytes:
        pass
