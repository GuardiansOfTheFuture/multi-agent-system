package com.paperai.controller;

import com.paperai.model.entity.KnowledgeGraph;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.service.KgExtractionService;
import com.paperai.service.KnowledgeGraphService;
import jakarta.annotation.Resource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

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

    /**
     * 上传文件并提取文本（支持 PDF / Word(.docx) / Markdown(.md)）
     */
    @PostMapping("/extract-file")
    public ApiResultVO<Map<String, Object>> extractFromFile(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        if (file.isEmpty()) return ApiResultVO.error("文件为空");
        String filename = file.getOriginalFilename();
        if (filename == null) return ApiResultVO.error("文件名无效");
        String lower = filename.toLowerCase();

        try {
            if (lower.endsWith(".pdf")) {
                return extractPdfText(file, filename);
            } else if (lower.endsWith(".docx")) {
                return extractDocxText(file, filename);
            } else if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".markdown")) {
                return extractPlainText(file, filename);
            } else {
                return ApiResultVO.error("不支持的文件格式，支持：PDF / Word(.docx) / Markdown(.md) / TXT");
            }
        } catch (Exception e) {
            return ApiResultVO.error("文件解析失败: " + e.getMessage());
        }
    }

    private ApiResultVO<Map<String, Object>> extractPdfText(MultipartFile file, String filename) throws Exception {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) return ApiResultVO.error("PDF 未提取到文本内容");
            return buildFileResult(text, filename, doc.getNumberOfPages());
        }
    }

    private ApiResultVO<Map<String, Object>> extractDocxText(MultipartFile file, String filename) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
            String text = sb.toString().trim();
            if (text.isEmpty()) return ApiResultVO.error("Word 文档未提取到文本内容");
            return buildFileResult(text, filename, null);
        }
    }

    private ApiResultVO<Map<String, Object>> extractPlainText(MultipartFile file, String filename) throws Exception {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (text.isBlank()) return ApiResultVO.error("文件内容为空");
        return buildFileResult(text, filename, null);
    }

    private ApiResultVO<Map<String, Object>> buildFileResult(String text, String filename, Integer pageCount) {
        String preview = text.length() > 500 ? text.substring(0, 500) + "..." : text;
        return ApiResultVO.success("提取成功", new java.util.LinkedHashMap<>() {{
            put("text", text);
            put("preview", preview);
            put("pageCount", pageCount);
            put("filename", filename);
            put("charCount", text.length());
        }});
    }
}
