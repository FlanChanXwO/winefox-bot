package com.github.winefoxbot.plugins.setu.model.dto;

import java.util.Collections;
import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-06-3:34
 */
public record SetuApiResponse (
    List<String> imgUrls,
    Boolean enabledR18
) {
    public SetuApiResponse () {
        this(Collections.emptyList(), false);
    }
}