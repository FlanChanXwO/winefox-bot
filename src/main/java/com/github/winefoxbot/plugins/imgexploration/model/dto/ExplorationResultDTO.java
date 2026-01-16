package com.github.winefoxbot.plugins.imgexploration.model.dto;

import java.util.List;

public record ExplorationResultDTO(
    List<SearchResultItemDTO> items
) {}