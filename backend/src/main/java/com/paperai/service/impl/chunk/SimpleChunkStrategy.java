package com.paperai.service.impl.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 基于段落边界 + 长度阈值切分，不依赖 LLM */
@Component
public class SimpleChunkStrategy implements ChunkStrategy {

    @Override
    public List<String> split(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        String[] paras = text.split("\n\n");
        StringBuilder buf = new StringBuilder();
        for (String p : paras) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            if (buf.length() + t.length() > maxLen && buf.length() > 100) {
                result.add(buf.toString().trim());
                buf = new StringBuilder(t);
            } else {
                if (buf.length() > 0) buf.append("\n\n");
                buf.append(t);
            }
        }
        if (buf.length() > 0) result.add(buf.toString().trim());
        return result;
    }
}
