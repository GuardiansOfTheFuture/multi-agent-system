package com.paperai.controller;

import com.paperai.agent.ResearcherAgent;
import com.paperai.model.ResearchResult;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.flow.FlowProfile;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.PaperVersion;
import com.paperai.model.entity.Task;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.model.vo.ResearchResultVO;
import com.paperai.service.AgentTaskService;
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
            } catch (Exception e) {
                log.error("异步写作异常: paperId={}, {}", paperId, e.getMessage(), e);
                stepEventPublisher.publishError(paperId, e.getMessage());
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
        String result = dashScopeChatClient.prompt().user(prompt).call().content();
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

    @GetMapping("/health")
    public ApiResultVO<String> health() {
        return ApiResultVO.success("PaperAI Backend is running");
    }

    @GetMapping("/flow/list")
    public ApiResultVO<List<Map<String, Object>>> flowList() {
        List<Map<String, Object>> list = FlowProfile.listAll().stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("name", f.getName());
            m.put("description", f.getDescription());
            return m;
        }).toList();
        return ApiResultVO.success(list);
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
