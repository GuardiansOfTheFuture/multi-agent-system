package com.paperai.service.impl.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperai.config.AiConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** LLM 语义分块：调用轻量模型按主题边界切分，失败回退到 SimpleChunkStrategy */
@Slf4j
@Component
public class SemanticChunkStrategy implements ChunkStrategy {

    @Resource private AiConfig aiConfig;
    @Resource private SimpleChunkStrategy fallback;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<String> split(String text, int maxLen) {
        try {
            String sample = text.length() > 20000 ? text.substring(0, 20000) : text;
            String prompt = """
                你是文档工程师。把以下文档按语义边界切成块，每块 500~2000 字。
                输出严格 JSON：[{"title":"章节标题","content":"块文本"}, ...]
                要求：标题概括内容；不在句子中间切；保留表格代码块完整；只输出 JSON 数组，无其他文字。

                文档：
                %s
                """.formatted(sample);
            String resp = aiConfig.callLightLlm("你是文档工程师，擅长分析文档结构。", prompt);
            int start = resp.indexOf('['), end = resp.lastIndexOf(']');
            if (start >= 0 && end > start) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = objectMapper.readValue(
                        resp.substring(start, end + 1), List.class);
                List<String> result = new ArrayList<>();
                for (var item : items) {
                    String title = (String) item.get("title");
                    String content = (String) item.get("content");
                    if (title != null && content != null) {
                        result.add("## " + title + "\n" + content);
                    } else if (content != null) {
                        result.add(content);
                    }
                }
                if (!result.isEmpty()) return result;
            }
        } catch (Exception e) {
            log.warn("LLM分块失败，回退简单切分: {}", e.getMessage());
        }
        return fallback.split(text, maxLen);
    }
}
