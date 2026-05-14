package com.paperai.service;

import com.paperai.model.entity.FlowDefinition;

import java.util.List;

public interface FlowDefinitionService {

    FlowDefinition create(FlowDefinition def);

    FlowDefinition getById(Long id);

    FlowDefinition getByIdAndUser(Long id, Long userId);

    List<FlowDefinition> listByUser(Long userId);

    FlowDefinition update(Long id, FlowDefinition def, Long userId);

    void delete(Long id, Long userId);

    FlowDefinition duplicate(Long id, Long userId);
}
