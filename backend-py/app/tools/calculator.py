"""
数据计算工具 — 执行数学计算和数据分析
"""
import logging
import math
import json
from .base import Tool

log = logging.getLogger("paperai.tools.calculator")


class CalculatorTool(Tool):
    """数据计算工具"""

    @property
    def name(self) -> str:
        return "calculator"

    @property
    def description(self) -> str:
        return "执行数学计算和数据分析。支持基本运算、统计分析（均值、标准差、中位数等）。用于论文中的数据分析和结果计算。"

    @property
    def parameters(self) -> dict:
        return {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["evaluate", "statistics", "correlation"],
                    "description": "操作类型: evaluate=表达式计算, statistics=统计分析, correlation=相关性分析"
                },
                "expression": {
                    "type": "string",
                    "description": "数学表达式（用于 evaluate），如 (2+3)*4, sqrt(16), log(100)"
                },
                "data": {
                    "type": "array",
                    "items": {"type": "number"},
                    "description": "数据数组（用于 statistics 和 correlation）"
                },
                "data_y": {
                    "type": "array",
                    "items": {"type": "number"},
                    "description": "第二组数据（用于 correlation 计算相关系数）"
                }
            },
            "required": ["operation"]
        }

    async def execute(self, operation: str, expression: str = "", data: list = None, data_y: list = None, **kwargs) -> str:
        """执行计算"""
        try:
            if operation == "evaluate":
                return self._evaluate(expression)
            elif operation == "statistics":
                return self._statistics(data or [])
            elif operation == "correlation":
                return self._correlation(data or [], data_y or [])
            else:
                return f"未知操作: {operation}"
        except Exception as e:
            log.error("计算失败: %s", e)
            return f"计算失败: {str(e)[:100]}"

    def _evaluate(self, expression: str) -> str:
        """安全计算数学表达式"""
        if not expression:
            return "请提供数学表达式"

        # 白名单：只允许数学函数和运算符
        allowed_names = {
            k: v for k, v in math.__dict__.items()
            if not k.startswith("_")
        }
        allowed_names.update({
            "abs": abs, "round": round, "min": min, "max": max,
            "sum": sum, "len": len, "int": int, "float": float,
        })

        # 安全检查
        forbidden = ["import", "exec", "eval", "__", "open", "file", "os", "sys"]
        for word in forbidden:
            if word in expression:
                return f"不允许使用: {word}"

        try:
            result = eval(expression, {"__builtins__": {}}, allowed_names)
            return f"计算结果: {expression} = {result}"
        except Exception as e:
            return f"表达式错误: {str(e)[:100]}"

    def _statistics(self, data: list) -> str:
        """统计分析"""
        if not data:
            return "请提供数据"

        n = len(data)
        mean = sum(data) / n
        sorted_data = sorted(data)

        # 中位数
        if n % 2 == 0:
            median = (sorted_data[n // 2 - 1] + sorted_data[n // 2]) / 2
        else:
            median = sorted_data[n // 2]

        # 标准差
        variance = sum((x - mean) ** 2 for x in data) / n
        std_dev = math.sqrt(variance)

        # 最大最小值
        min_val = min(data)
        max_val = max(data)

        # 四分位数
        q1_idx = n // 4
        q3_idx = 3 * n // 4
        q1 = sorted_data[q1_idx]
        q3 = sorted_data[q3_idx]
        iqr = q3 - q1

        result = f"""统计分析结果:
样本数量: {n}
均值: {mean:.4f}
中位数: {median:.4f}
标准差: {std_dev:.4f}
方差: {variance:.4f}
最小值: {min_val}
最大值: {max_val}
范围: {max_val - min_val}
Q1 (25%): {q1}
Q3 (75%): {q3}
四分位距 (IQR): {iqr}"""
        return result

    def _correlation(self, data_x: list, data_y: list) -> str:
        """计算相关系数"""
        if not data_x or not data_y:
            return "请提供两组数据"
        if len(data_x) != len(data_y):
            return "两组数据长度必须相同"

        n = len(data_x)
        mean_x = sum(data_x) / n
        mean_y = sum(data_y) / n

        # 计算相关系数
        numerator = sum((x - mean_x) * (y - mean_y) for x, y in zip(data_x, data_y))
        denom_x = math.sqrt(sum((x - mean_x) ** 2 for x in data_x))
        denom_y = math.sqrt(sum((y - mean_y) ** 2 for y in data_y))

        if denom_x == 0 or denom_y == 0:
            return "无法计算相关系数（数据方差为0）"

        correlation = numerator / (denom_x * denom_y)

        # 解释
        if abs(correlation) >= 0.8:
            strength = "强"
        elif abs(correlation) >= 0.5:
            strength = "中等"
        elif abs(correlation) >= 0.3:
            strength = "弱"
        else:
            strength = "极弱"

        direction = "正" if correlation > 0 else "负"

        result = f"""相关性分析结果:
皮尔逊相关系数: {correlation:.4f}
相关性: {strength}{direction}相关
样本数量: {n}

解释:
- 相关系数范围 [-1, 1]
- |r| >= 0.8: 强相关
- 0.5 <= |r| < 0.8: 中等相关
- 0.3 <= |r| < 0.5: 弱相关
- |r| < 0.3: 极弱相关"""
        return result
