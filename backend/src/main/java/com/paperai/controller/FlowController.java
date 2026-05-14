package com.paperai.controller;

import com.paperai.model.entity.FlowDefinition;
import com.paperai.model.flow.FlowProfile;
import com.paperai.model.vo.ApiResultVO;
import com.paperai.service.FlowDefinitionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 流程定义 API
 *
 * @author ch
 * @date 2026年05月14日
 */
@Slf4j
@RestController
@RequestMapping("/api/flow")
public class FlowController {

    @Resource
    private FlowDefinitionService flowDefinitionService;

    /**
     * 流程列表 — 合并预设流程 + 用户自定义流程
     */
    @GetMapping("/list")
    public ApiResultVO<List<Map<String, Object>>> list(Authentication auth) {
        Long userId = userId(auth);
        List<Map<String, Object>> result = new ArrayList<>();

        // 预设流程（FlowProfile 枚举）
        for (FlowProfile f : FlowProfile.listAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("name", f.getName());
            m.put("description", f.getDescription());
            m.put("category", "preset");
            m.put("source", "preset");
            result.add(m);
        }

        // 用户自定义流程
        List<FlowDefinition> userFlows = flowDefinitionService.listByUser(userId);
        for (FlowDefinition f : userFlows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", "custom-" + f.getId());
            m.put("dbId", f.getId());
            m.put("name", f.getName());
            m.put("description", f.getDescription());
            m.put("category", f.getCategory());
            m.put("source", "custom");
            m.put("isTemplate", f.getIsTemplate());
            m.put("updatedAt", f.getUpdatedAt());
            result.add(m);
        }

        return ApiResultVO.success(result);
    }

    /**
     * 获取单个流程详情（含 graphData）
     */
    @GetMapping("/{id}")
    public ApiResultVO<FlowDefinition> detail(@PathVariable Long id, Authentication auth) {
        return ApiResultVO.success(flowDefinitionService.getByIdAndUser(id, userId(auth)));
    }

    /**
     * 创建流程
     */
    @PostMapping
    public ApiResultVO<FlowDefinition> create(@RequestBody FlowDefinition def, Authentication auth) {
        def.setUserId(userId(auth));
        if (def.getCategory() == null) def.setCategory("custom");
        if (def.getIsTemplate() == null) def.setIsTemplate(0);
        return ApiResultVO.success("流程已创建", flowDefinitionService.create(def));
    }

    /**
     * 更新流程
     */
    @PutMapping("/{id}")
    public ApiResultVO<FlowDefinition> update(@PathVariable Long id, @RequestBody FlowDefinition def, Authentication auth) {
        return ApiResultVO.success("流程已更新", flowDefinitionService.update(id, def, userId(auth)));
    }

    /**
     * 删除流程
     */
    @DeleteMapping("/{id}")
    public ApiResultVO<String> delete(@PathVariable Long id, Authentication auth) {
        flowDefinitionService.delete(id, userId(auth));
        return ApiResultVO.success("流程已删除");
    }

    /**
     * 复制流程
     */
    @PostMapping("/{id}/duplicate")
    public ApiResultVO<FlowDefinition> duplicate(@PathVariable Long id, Authentication auth) {
        return ApiResultVO.success("流程已复制", flowDefinitionService.duplicate(id, userId(auth)));
    }

    private Long userId(Authentication auth) {
        return auth != null ? (Long) auth.getPrincipal() : 0L;
    }
}
