"""
图表生成工具 — 生成 ECharts 配置和 Mermaid 代码
"""
import json
import logging
from .base import Tool

log = logging.getLogger("paperai.tools.chart")


class ChartGeneratorTool(Tool):
    """图表生成工具"""

    @property
    def name(self) -> str:
        return "generate_chart"

    @property
    def description(self) -> str:
        return "生成数据可视化图表。支持 ECharts（柱状图、折线图、饼图、散点图）和 Mermaid（流程图、时序图、架构图）。返回可直接嵌入论文的代码。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "chart_type": {
                    "type": "string",
                    "enum": ["bar", "line", "pie", "scatter", "flowchart", "sequence", "architecture"],
                    "description": "图表类型: bar=柱状图, line=折线图, pie=饼图, scatter=散点图, flowchart=流程图, sequence=时序图, architecture=架构图"
                },
                "title": {
                    "type": "string",
                    "description": "图表标题"
                },
                "data": {
                    "type": "object",
                    "description": "图表数据。对于 ECharts: {labels: [...], values: [...]}。对于 Mermaid: {nodes: [...], edges: [...]}"
                },
                "description": {
                    "type": "string",
                    "description": "图表描述，说明图表展示的内容"
                }
            },
            "required": ["chart_type", "title", "data"]
        }

    async def execute(self, chart_type: str, title: str, data: dict, description: str = "", **kwargs) -> str:
        """生成图表代码"""
        try:
            if chart_type in ["bar", "line", "pie", "scatter"]:
                return self._generate_echarts(chart_type, title, data, description)
            elif chart_type in ["flowchart", "sequence", "architecture"]:
                return self._generate_mermaid(chart_type, title, data, description)
            else:
                return f"不支持的图表类型: {chart_type}"
        except Exception as e:
            log.error("图表生成失败: %s", e)
            return f"图表生成失败: {str(e)[:100]}"

    def _generate_echarts(self, chart_type: str, title: str, data: dict, description: str) -> str:
        """生成 ECharts 配置"""
        labels = data.get("labels", [])
        values = data.get("values", [])
        x_label = data.get("x_label", "")
        y_label = data.get("y_label", "")

        if chart_type == "bar":
            config = {
                "title": {"text": title},
                "tooltip": {"trigger": "axis"},
                "xAxis": {"type": "category", "data": labels, "name": x_label},
                "yAxis": {"type": "value", "name": y_label},
                "series": [{"type": "bar", "data": values, "name": title}]
            }
        elif chart_type == "line":
            config = {
                "title": {"text": title},
                "tooltip": {"trigger": "axis"},
                "xAxis": {"type": "category", "data": labels, "name": x_label},
                "yAxis": {"type": "value", "name": y_label},
                "series": [{"type": "line", "data": values, "name": title, "smooth": True}]
            }
        elif chart_type == "pie":
            pie_data = [{"name": l, "value": v} for l, v in zip(labels, values)]
            config = {
                "title": {"text": title},
                "tooltip": {"trigger": "item"},
                "series": [{"type": "pie", "data": pie_data, "radius": "60%"}]
            }
        elif chart_type == "scatter":
            config = {
                "title": {"text": title},
                "tooltip": {"trigger": "item"},
                "xAxis": {"type": "value", "name": x_label},
                "yAxis": {"type": "value", "name": y_label},
                "series": [{"type": "scatter", "data": values, "name": title}]
            }
        else:
            return f"不支持的 ECharts 类型: {chart_type}"

        chart_json = json.dumps(config, ensure_ascii=False, indent=2)
        result = f"图表: {title}\n"
        if description:
            result += f"说明: {description}\n"
        result += f"\n```chart-json\n{chart_json}\n```"
        return result

    def _generate_mermaid(self, chart_type: str, title: str, data: dict, description: str) -> str:
        """生成 Mermaid 代码"""
        nodes = data.get("nodes", [])
        edges = data.get("edges", [])

        if chart_type == "flowchart":
            lines = ["graph TD"]
            for node in nodes:
                node_id = node.get("id", "")
                node_label = node.get("label", "")
                lines.append(f"    {node_id}[{node_label}]")
            for edge in edges:
                src = edge.get("from", "")
                dst = edge.get("to", "")
                label = edge.get("label", "")
                if label:
                    lines.append(f"    {src} -->|{label}| {dst}")
                else:
                    lines.append(f"    {src} --> {dst}")
            mermaid_code = "\n".join(lines)

        elif chart_type == "sequence":
            lines = ["sequenceDiagram"]
            for edge in edges:
                src = edge.get("from", "")
                dst = edge.get("to", "")
                msg = edge.get("message", "")
                lines.append(f"    {src}->>{dst}: {msg}")
            mermaid_code = "\n".join(lines)

        elif chart_type == "architecture":
            lines = ["graph LR"]
            for node in nodes:
                node_id = node.get("id", "")
                node_label = node.get("label", "")
                lines.append(f"    {node_id}[{node_label}]")
            for edge in edges:
                src = edge.get("from", "")
                dst = edge.get("to", "")
                lines.append(f"    {src} --> {dst}")
            mermaid_code = "\n".join(lines)

        else:
            return f"不支持的 Mermaid 类型: {chart_type}"

        result = f"图表: {title}\n"
        if description:
            result += f"说明: {description}\n"
        result += f"\n```mermaid\n{mermaid_code}\n```"
        return result
