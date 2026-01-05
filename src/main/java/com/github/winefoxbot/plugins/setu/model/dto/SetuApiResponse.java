package com.github.winefoxbot.plugins.setu.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-06-3:34
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetuApiResponse {
    private String imgUrl;
    private Boolean enabledR18 = false;
}