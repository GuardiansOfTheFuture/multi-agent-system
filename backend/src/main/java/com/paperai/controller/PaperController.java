package com.paperai.controller;

import com.paperai.agent.ResearcherAgent;
import com.paperai.model.ResearchResult;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.PaperVersion;
import com.paperai.model.entity.Task;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.model.vo.ResearchResultVO;
import com.paperai.service.AgentTaskService;
import com.paperai.service.LlmCacheService;
import com.paperai.service.OrchestratorService;
import com.paperai.service.PaperService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 论文写作 API 控制器
 *
 * @author: ch
 * @date 2026年05月11日
 */
@Slf4j
@RestController
@RequestMapping("/api/paper")
public class PaperController {

    @Resource private ResearcherAgent researcherAgent;
    @Resource private OrchestratorService orchestratorService;
    @Resource private PaperService paperService;
    @Resource private AgentTaskService agentTaskService;
    @Resource private com.paperai.service.StepEventPublisher stepEventPublisher;
    @Resource private ChatClient dashScopeChatClient;
    @Resource private LlmCacheService llmCacheService;
    @Resource private com.paperai.service.ReferenceService referenceService;
    @Resource private com.paperai.service.ExportService exportService;



    // ===== 全流程写作 =====

    @PostMapping("/create")
    public ApiResultVO<Map<String, Object>> createPaper(
            @RequestBody PaperWritingRequestDTO request,
            Authentication auth) {
        Long userId = userId(auth);
        Paper paper = paperService.createPaper(request, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("paperId", paper.getId());
        result.put("topic", paper.getTitle());
        result.put("status", paper.getStatus());
        return ApiResultVO.success("论文已创建", result);
    }

    @PostMapping("/write/{paperId}")
    public ApiResultVO<Map<String, Object>> startWriting(
            @PathVariable Long paperId,
            @RequestBody PaperWritingRequestDTO request,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        Thread.startVirtualThread(() -> {
            try {
                orchestratorService.executeAsync(paperId, request);
            } catch (Throwable e) {
                log.error("异步写作异常: paperId={}, {}", paperId, e.getMessage(), e);
                try {
                    stepEventPublisher.publishError(paperId, e.getMessage() != null ? e.getMessage() : "写作异常");
                } catch (Throwable ignored) {}
            }
        });
        Map<String, Object> result = new HashMap<>();
        result.put("paperId", paperId); result.put("status", "STARTED");
        return ApiResultVO.success("写作任务已启动", result);
    }

    @PostMapping("/write")
    public ApiResultVO<PaperWritingVO> writePaper(
            @RequestBody PaperWritingRequestDTO request,
            Authentication auth) {
        return ApiResultVO.success(orchestratorService.execute(request, userId(auth)));
    }

    @PostMapping("/write/{paperId}/stop")
    public ApiResultVO<Map<String, Object>> stopWriting(
            @PathVariable Long paperId,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        orchestratorService.stopTask(paperId);
        return ApiResultVO.success("已发送停止请求", Map.of("paperId", paperId, "status", "STOPPED"));
    }

    /**
     * SSE 端点 — 前端通过 EventSource 订阅写作进度
     * 连接建立后，后端异步推送 step/stream/complete/error 事件
     */
    @GetMapping(value = "/write/{paperId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamWritingProgress(
            @PathVariable Long paperId,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        log.info("SSE 连接: paperId={}", paperId);
        return stepEventPublisher.createEmitter(paperId);
    }

    @PostMapping(value = "/write/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PaperWritingVO.StepRecordVO> writePaperStream(
            @RequestBody PaperWritingRequestDTO request,
            Authentication auth) {
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    orchestratorService.executeStream(request, step -> sink.next(step), userId(auth));
                    sink.complete();
                } catch (Exception e) { sink.error(e); }
            });
        });
    }

    // ===== 版本管理 =====

    @GetMapping("/{id}/versions")
    public ApiResultVO<List<com.paperai.model.entity.PaperVersion>> versions(@PathVariable Long id) {
        return ApiResultVO.success(paperService.getVersions(id));
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public ApiResultVO<com.paperai.model.entity.PaperVersion> versionDetail(
            @PathVariable Long id, @PathVariable Integer versionNo) {
        return ApiResultVO.success(paperService.getVersion(id, versionNo));
    }

    @GetMapping("/{id}/versions/latest")
    public ApiResultVO<com.paperai.model.entity.PaperVersion> latestVersion(@PathVariable Long id) {
        return ApiResultVO.success(paperService.getLatestVersion(id));
    }

    // ===== 单步研究 =====

    @PostMapping("/research")
    public ApiResultVO<ResearchResultVO> doResearch(@RequestBody ResearchRequestDTO request) {
        ResearchResult result = researcherAgent.executeStructuredResearch(request);
        return ApiResultVO.success("研究完成", toResearchResultVO(result));
    }

    @PostMapping(value = "/research/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doResearchStream(@RequestBody ResearchRequestDTO request) {
        return Flux.just(researcherAgent.executeStructuredResearch(request).getRawResponse());
    }

    // ===== 论文管理 CRUD =====

    @GetMapping("/list")
    public ApiResultVO<List<Paper>> list(Authentication auth) {
        return ApiResultVO.success(paperService.listByUserId(userId(auth)));
    }

    @GetMapping("/{id}")
    public ApiResultVO<Paper> detail(@PathVariable Long id, Authentication auth) {
        Paper paper = paperService.getPaperById(id);
        paperService.checkOwner(id, userId(auth));
        PaperVersion latest = paperService.getLatestVersion(id);
        if (latest != null) {
            paper.setContent(latest.getContent());
        }
        return ApiResultVO.success(paper);
    }

    @GetMapping("/{id}/tasks")
    public ApiResultVO<Map<String, Object>> tasks(@PathVariable Long id, Authentication auth) {
        paperService.checkOwner(id, userId(auth));
        Paper paper = paperService.getPaperById(id);
        PaperVersion latest = paperService.getLatestVersion(id);
        if (latest != null) {
            paper.setContent(latest.getContent());
        }
        List<Task> tasks = agentTaskService.getTasksByPaperId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("paper", paper);
        result.put("tasks", tasks);
        return ApiResultVO.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResultVO<String> delete(@PathVariable Long id, Authentication auth) {
        paperService.checkOwner(id, userId(auth));
        paperService.deletePaper(id);
        return ApiResultVO.success("删除成功");
    }

    // ===== 手动编辑 & Agent 修改 =====

    /**
     * 手动更新论文内容（编辑模式直接保存）
     */
    @PutMapping("/{id}/content")
    public ApiResultVO<Map<String, Object>> updateContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        paperService.checkOwner(id, userId(auth));
        String content = body.get("content");
        String versionNoStr = body.get("versionNo");
        Integer versionNo = versionNoStr != null ? Integer.valueOf(versionNoStr) : null;
        paperService.updateContent(id, versionNo, content);
        return ApiResultVO.success("内容已更新", Map.of("paperId", id));
    }

    /**
     * Agent 修改选中文本
     * 接收选中的文本段 + 修改指令，由 AI Agent 处理后再替换
     */
    @PostMapping("/{id}/agent-edit")
    public ApiResultVO<Map<String, Object>> agentEdit(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        paperService.checkOwner(id, userId(auth));
        String selectedText = body.get("selectedText");
        String instruction = body.get("instruction");
        if (selectedText == null || selectedText.isBlank()) {
            return ApiResultVO.error("请选中要修改的文本");
        }
        if (instruction == null || instruction.isBlank()) {
            return ApiResultVO.error("请输入修改指令");
        }
        // 调用 AI 修改
        String prompt = String.format("""
                你是一位专业的学术论文编辑助手。
                请根据以下指令，修改选中的论文文本。
                只返回修改后的文本，不要添加额外解释。

                选中文本：
                %s

                修改指令：
                %s
                """, selectedText, instruction);
        String cacheKey = llmCacheService.computeKey(null, prompt);
        String cached = llmCacheService.get(cacheKey);
        String result = cached != null ? cached : dashScopeChatClient.prompt().user(prompt).call().content();
        if (cached == null && result != null) llmCacheService.put(cacheKey, result);
        return ApiResultVO.success(Map.of(
                "originalText", selectedText,
                "modifiedText", result
        ));
    }

    /**
     * 保存新版本（手动编辑后，带上版本日志）
     */
    @PostMapping("/{id}/versions")
    public ApiResultVO<Map<String, Object>> saveVersion(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        paperService.checkOwner(id, userId(auth));
        String content = body.get("content");
        String summary = body.get("summary");
        if (content == null) {
            return ApiResultVO.error("内容不能为空");
        }
        if (summary == null || summary.isBlank()) {
            return ApiResultVO.error("请填写版本日志");
        }
        String editType = body.getOrDefault("editType", "MANUAL");
        String changeSummary = body.get("changeSummary");
        PaperVersion pv = paperService.saveVersion(id, "MANUAL_EDIT", summary, content,
                editType, changeSummary);
        return ApiResultVO.success("版本已保存", Map.of(
                "paperId", id,
                "versionNo", pv.getVersionNo(),
                "versionId", pv.getId()
        ));
    }

    // ===== 参考文献管理 =====

    @GetMapping("/{paperId}/references")
    public ApiResultVO<List<com.paperai.model.entity.Reference>> listReferences(
            @PathVariable Long paperId, Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        return ApiResultVO.success(referenceService.listByPaperId(paperId));
    }

    @PostMapping("/{paperId}/references")
    public ApiResultVO<com.paperai.model.entity.Reference> addReference(
            @PathVariable Long paperId,
            @RequestBody com.paperai.model.entity.Reference ref,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        return ApiResultVO.success("添加成功", referenceService.add(paperId, ref));
    }

    @PutMapping("/{paperId}/references/{refId}")
    public ApiResultVO<com.paperai.model.entity.Reference> updateReference(
            @PathVariable Long paperId,
            @PathVariable Long refId,
            @RequestBody com.paperai.model.entity.Reference ref,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        return ApiResultVO.success("更新成功", referenceService.update(refId, ref, userId(auth)));
    }

    @DeleteMapping("/{paperId}/references/{refId}")
    public ApiResultVO<String> deleteReference(
            @PathVariable Long paperId,
            @PathVariable Long refId,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        referenceService.delete(refId, userId(auth));
        return ApiResultVO.success("删除成功");
    }

    @PostMapping("/{paperId}/references/import-bibtex")
    public ApiResultVO<Map<String, Object>> importBibtex(
            @PathVariable Long paperId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        String bibtex = body.get("bibtex");
        if (bibtex == null || bibtex.isBlank()) {
            return ApiResultVO.error("BibTeX 内容不能为空");
        }
        int count = referenceService.importBibtex(paperId, bibtex);
        return ApiResultVO.success("导入成功", Map.of("count", count));
    }

    @PostMapping("/{paperId}/references/extract")
    public ApiResultVO<List<com.paperai.model.entity.Reference>> extractReferences(
            @PathVariable Long paperId,
            Authentication auth) {
        paperService.checkOwner(paperId, userId(auth));
        com.paperai.model.entity.Paper paper = paperService.getPaperById(paperId);
        String researchOutput = null;
        var tasks = agentTaskService.getTasksByPaperId(paperId);
        for (var t : tasks) {
            if ("RESEARCHER".equals(t.getAgentRole()) && t.getOutputData() != null) {
                researchOutput = t.getOutputData();
                break;
            }
        }
        if (researchOutput == null) {
            return ApiResultVO.error("未找到研究输出，请先执行研究步骤");
        }
        List<com.paperai.model.entity.Reference> refs = referenceService.extractFromResearchOutput(paperId, researchOutput);
        return ApiResultVO.success("提取成功", refs);
    }

    // ===== 论文导出 =====

    @GetMapping("/{id}/export")
    public org.springframework.http.ResponseEntity<byte[]> exportPaper(
            @PathVariable Long id,
            @RequestParam(defaultValue = "docx") String format,
            @RequestParam(required = false) Integer versionNo,
            Authentication auth) {
        paperService.checkOwner(id, userId(auth));

        byte[] data;
        String filename;
        String contentType;
        com.paperai.model.entity.Paper paper = paperService.getPaperById(id);
        String title = paper.getTitle() != null ? paper.getTitle() : "paper";
        String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeTitle.length() > 50) safeTitle = safeTitle.substring(0, 50);

        switch (format.toLowerCase()) {
            case "pdf" -> {
                data = exportService.toPdf(id, versionNo);
                filename = safeTitle + ".pdf";
                contentType = "application/pdf";
            }
            case "latex" -> {
                data = exportService.toLatex(id, versionNo);
                filename = safeTitle + ".tex";
                contentType = "application/x-latex";
            }
            case "html" -> {
                data = exportService.toHtml(
                        versionNo != null
                                ? paperService.getVersion(id, versionNo).getContent()
                                : paperService.getLatestVersion(id).getContent()
                ).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                filename = safeTitle + ".html";
                contentType = "text/html; charset=UTF-8";
            }
            default -> {
                data = exportService.toDocx(id, versionNo);
                filename = safeTitle + ".docx";
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
        }

        // 使用 ContentDisposition 避免中文文件名被 URL 编码为 %E5%9F%BA... 格式
        org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition
                .attachment()
                .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .body(data);
    }

    @GetMapping("/health")
    public ApiResultVO<String> health() {
        return ApiResultVO.success("PaperAI Backend is running");
    }

    /** 从 Authentication 提取 userId */
    private Long userId(Authentication auth) {
        return auth != null ? (Long) auth.getPrincipal() : 0L;
    }

    // ===== 转换 =====

    private ResearchResultVO toResearchResultVO(ResearchResult result) {
        ResearchResultVO vo = new ResearchResultVO();
        vo.setTaskId(result.getTaskId());
        vo.setTopic(result.getTopic());
        vo.setStatus(result.getStatus());
        vo.setLiteratureReview(result.getLiteratureReview());
        vo.setKeyFindings(result.getKeyFindings());
        vo.setSuggestedDirections(result.getSuggestedDirections());
        vo.setReferences(result.getReferences());
        vo.setRawResponse(result.getRawResponse());
        vo.setStartTime(result.getStartTime());
        vo.setEndTime(result.getEndTime());
        vo.setDurationMs(result.getDurationMs());
        return vo;
    }
}
