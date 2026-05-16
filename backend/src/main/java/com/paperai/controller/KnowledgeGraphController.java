package com.paperai.controller;

import com.paperai.model.entity.KnowledgeGraph;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.service.KgExtractionService;
import com.paperai.service.KnowledgeGraphService;
import jakarta.annotation.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kg")
public class KnowledgeGraphController {

    @Resource
    private KnowledgeGraphService knowledgeGraphService;
    @Resource
    private KgExtractionService kgExtractionService;

    private Long userId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }

    @GetMapping
    public ApiResultVO<List<KnowledgeGraph>> list(
            @RequestParam(required = false) Long paperId,
            Authentication auth) {
        if (paperId != null) {
            return ApiResultVO.success(knowledgeGraphService.listByPaper(paperId));
        }
        return ApiResultVO.success(knowledgeGraphService.listByUser(userId(auth)));
    }

    @GetMapping("/{id}")
    public ApiResultVO<KnowledgeGraph> getById(@PathVariable Long id, Authentication auth) {
        return ApiResultVO.success(knowledgeGraphService.getByIdAndUser(id, userId(auth)));
    }

    @PostMapping
    public ApiResultVO<KnowledgeGraph> create(@RequestBody KnowledgeGraph kg, Authentication auth) {
        kg.setUserId(userId(auth));
        return ApiResultVO.success("创建成功", knowledgeGraphService.create(kg));
    }

    @PutMapping("/{id}")
    public ApiResultVO<KnowledgeGraph> update(@PathVariable Long id, @RequestBody KnowledgeGraph kg, Authentication auth) {
        return ApiResultVO.success("更新成功", knowledgeGraphService.update(id, kg, userId(auth)));
    }

    @DeleteMapping("/{id}")
    public ApiResultVO<String> delete(@PathVariable Long id, Authentication auth) {
        knowledgeGraphService.delete(id, userId(auth));
        return ApiResultVO.success("已删除");
    }

    @PostMapping("/{id}/duplicate")
    public ApiResultVO<KnowledgeGraph> duplicate(@PathVariable Long id, Authentication auth) {
        return ApiResultVO.success("复制成功", knowledgeGraphService.duplicate(id, userId(auth)));
    }

    // ===== AI 抽取 =====

    @SuppressWarnings("unchecked")
    @PostMapping("/extract")
    public ApiResultVO<String> extractFromText(@RequestBody Map<String, Object> body) {
        String text = (String) body.get("text");
        String topic = (String) body.get("topic");
        List<String> entityTypes = (List<String>) body.get("entityTypes");
        List<String> relationTypes = (List<String>) body.get("relationTypes");
        double confidence = body.get("confidence") instanceof Number n ? n.doubleValue() : 0.7;
        return ApiResultVO.success("抽取完成", kgExtractionService.extractFromText(text, topic, entityTypes, relationTypes, confidence));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/extract-from-paper/{paperId}")
    public ApiResultVO<String> extractFromPaper(@PathVariable Long paperId, @RequestBody Map<String, Object> body) {
        List<String> entityTypes = (List<String>) body.get("entityTypes");
        List<String> relationTypes = (List<String>) body.get("relationTypes");
        double confidence = body.get("confidence") instanceof Number n ? n.doubleValue() : 0.7;
        return ApiResultVO.success("抽取完成", kgExtractionService.extractFromPaper(paperId, entityTypes, relationTypes, confidence));
    }
}
