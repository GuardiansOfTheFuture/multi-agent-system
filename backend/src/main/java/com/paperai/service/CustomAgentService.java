package com.paperai.service;

import com.paperai.model.entity.CustomAgent;

import java.util.List;

public interface CustomAgentService {

    List<CustomAgent> listByUser(Long userId);

    CustomAgent getById(Long id);

    CustomAgent create(CustomAgent agent);

    CustomAgent update(Long id, CustomAgent agent, Long userId);

    void delete(Long id, Long userId);
}
