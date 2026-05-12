package com.paperai.controller;

import com.paperai.agent.ResearcherAgent;
import com.paperai.model.ResearchResult;
import com.paperai.model.dto.PaperWritingRequestDTO;
import com.paperai.model.dto.ResearchRequestDTO;
import com.paperai.model.entity.Paper;
import com.paperai.model.entity.Task;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.model.vo.PaperWritingVO;
import com.paperai.model.vo.ResearchResultVO;
import com.paperai.service.AgentTaskService;
import com.paperai.service.OrchestratorService;
import com.paperai.service.PaperService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
        return ApiResultVO.success(paper);
    }

    @GetMapping("/{id}/tasks")
    public ApiResultVO<Map<String, Object>> tasks(@PathVariable Long id, Authentication auth) {
        paperService.checkOwner(id, userId(auth));
        Paper paper = paperService.getPaperById(id);
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
