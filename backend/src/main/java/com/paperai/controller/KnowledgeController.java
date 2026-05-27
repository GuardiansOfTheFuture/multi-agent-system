package com.paperai.controller;

import com.paperai.model.entity.KnowledgeChunk;
import com.paperai.model.entity.KnowledgeDocument;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.service.KnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Resource private KnowledgeService knowledgeService;

    private Long userId(Authentication auth) {
        return auth != null ? (Long) auth.getPrincipal() : 0L;
    }

    @PostMapping("/upload")
    public ApiResultVO<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "PRIVATE") String scope,
            Authentication auth) {
        try {
            KnowledgeDocument doc = knowledgeService.upload(file, userId(auth), scope);
            return ApiResultVO.success("入库成功", Map.of(
                    "docId", doc.getId(),
                    "status", "COMPLETED",
                    "totalChunks", doc.getTotalChunks() != null ? doc.getTotalChunks() : 0
            ));
        } catch (Exception e) {
            log.error("入库失败", e);
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            return ApiResultVO.error("入库失败: " + msg);
        }
    }

    @GetMapping("/my")
    public ApiResultVO<List<KnowledgeDocument>> myDocs(Authentication auth) {
        return ApiResultVO.success(knowledgeService.listByUser(userId(auth)));
    }

    @GetMapping("/shared")
    public ApiResultVO<List<KnowledgeDocument>> sharedDocs() {
        return ApiResultVO.success(knowledgeService.listShared());
    }

    @GetMapping("/{docId}/chunks")
    public ApiResultVO<List<Map<String, Object>>> chunks(@PathVariable Long docId) {
        var chunks = knowledgeService.getChunks(docId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var c : chunks) {
            String text = c.getContent();
            result.add(Map.of("chunkIndex", c.getChunkIndex(),
                    "content", text,
                    "charCount", c.getCharCount()));
        }
        return ApiResultVO.success(result);
    }

    @GetMapping("/search")
    public ApiResultVO<List<Map<String, Object>>> search(
            @RequestParam String q, @RequestParam(defaultValue = "5") int k, Authentication auth) {
        var docs = knowledgeService.search(q, k, userId(auth));
        List<Map<String, Object>> result = new ArrayList<>();
        for (var d : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", d.getText().length() > 300 ? d.getText().substring(0, 300) + "..." : d.getText());
            m.put("title", d.getMetadata().getOrDefault("docTitle", ""));
            result.add(m);
        }
        return ApiResultVO.success(result);
    }

    @DeleteMapping("/{docId}")
    public ApiResultVO<String> delete(@PathVariable Long docId, Authentication auth) {
        try { knowledgeService.delete(docId, userId(auth)); return ApiResultVO.success("已删除"); }
        catch (Exception e) { return ApiResultVO.error(e.getMessage()); }
    }
}
