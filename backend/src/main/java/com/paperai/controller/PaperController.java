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
import org.springframework.http.MediaType;
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
@RestController
@RequestMapping("/api/paper")
public class PaperController {

    @Resource
    private ResearcherAgent researcherAgent;

    @Resource
    private OrchestratorService orchestratorService;

    @Resource
    private PaperService paperService;

    @Resource
    private AgentTaskService agentTaskService;

    // ===== 全流程写作 =====

    @PostMapping("/write")
    public ApiResultVO<PaperWritingVO> writePaper(@RequestBody PaperWritingRequestDTO request) {
        PaperWritingVO result = orchestratorService.execute(request);
        return ApiResultVO.success("论文写作完成", result);
    }

    /** SSE 流式推送 — 每完成一步推一条 StepRecordVO */
    @PostMapping(value = "/write/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PaperWritingVO.StepRecordVO> writePaperStream(@RequestBody PaperWritingRequestDTO request) {
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    orchestratorService.executeStream(request, step -> {
                        sink.next(step);
                    });
                    sink.complete();
                } catch (Exception e) {
                    sink.error(e);
                }
            });
        });
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
    public ApiResultVO<List<Paper>> list() {
        return ApiResultVO.success(paperService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResultVO<Paper> detail(@PathVariable Long id) {
        Paper paper = paperService.getPaperById(id);
        return ApiResultVO.success(paper);
    }

    @GetMapping("/{id}/tasks")
    public ApiResultVO<Map<String, Object>> tasks(@PathVariable Long id) {
        Paper paper = paperService.getPaperById(id);
        List<Task> tasks = agentTaskService.getTasksByPaperId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("paper", paper);
        result.put("tasks", tasks);
        return ApiResultVO.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResultVO<Void> delete(@PathVariable Long id) {
        paperService.deletePaper(id);
        return ApiResultVO.success("删除成功", null);
    }

    // ===== 健康检查 =====

    @GetMapping("/health")
    public ApiResultVO<String> health() {
        return ApiResultVO.success("PaperAI Backend is running");
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
