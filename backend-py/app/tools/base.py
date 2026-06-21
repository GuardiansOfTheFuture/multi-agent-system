"""
Tool 基类和注册表
所有 Agent 工具都继承 Tool 基类，通过 ToolRegistry 统一管理
"""
import logging
from abc import ABC, abstractmethod
from typing import Any

log = logging.getLogger("paperai.tools")


class Tool(ABC):
    """工具基类"""

    @property
    @abstractmethod
    def name(self) -> str:
        """工具名称（英文，用于 function calling）"""
        ...

    @property
    @abstractmethod
    def description(self) -> str:
        """工具描述（给 LLM 看，说明何时使用）"""
        ...

    @property
    @abstractmethod
    def parameters(self) -> dict:
        """参数 JSON Schema（给 LLM 看，说明如何调用）"""
        ...

    @abstractmethod
    async def execute(self, **kwargs) -> str:
        """执行工具，返回结果文本"""
        ...

    def to_schema(self) -> dict:
        """转换为 OpenAI function calling 格式"""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            }
        }


class ToolRegistry:
    """工具注册表"""

    def __init__(self):
        self._tools: dict[str, Tool] = {}

    def register(self, tool: Tool) -> None:
        """注册工具"""
        self._tools[tool.name] = tool
        log.info("[ToolRegistry] 注册工具: %s", tool.name)

    def get(self, name: str) -> Tool | None:
        """获取工具"""
        return self._tools.get(name)

    def list_tools(self) -> list[Tool]:
        """列出所有工具"""
        return list(self._tools.values())

    def get_schemas(self) -> list[dict]:
        """获取所有工具的 JSON Schema（用于 function calling）"""
        return [tool.to_schema() for tool in self._tools.values()]

    def get_schemas_by_names(self, names: list[str]) -> list[dict]:
        """按名称获取指定工具的 JSON Schema"""
        return [self._tools[name].to_schema() for name in names if name in self._tools]

    async def call(self, name: str, **kwargs) -> str:
        """调用工具"""
        tool = self._tools.get(name)
        if not tool:
            return f"错误：工具 '{name}' 不存在"
        try:
            log.info("[ToolRegistry] 调用工具: %s args=%s", name, list(kwargs.keys()))
            result = await tool.execute(**kwargs)
            log.info("[ToolRegistry] 工具 %s 返回 %d 字", name, len(result) if result else 0)
            return result
        except Exception as e:
            log.error("[ToolRegistry] 工具 %s 执行失败: %s", name, e)
            return f"工具执行失败: {str(e)[:200]}"


# 全局工具注册表
tool_registry = ToolRegistry()
