package com.paperai.service.impl.chunk;

import java.util.List;

/** 文本分块策略 */
public interface ChunkStrategy {
    List<String> split(String text, int maxLen);
}
