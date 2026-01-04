package com.github.winefoxbot.core.model.dto;

import com.github.winefoxbot.core.model.enums.BotReplyTemplateType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-20:59
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextReplyParams {
    private String username;
    private @NotNull BotReplyTemplateType type;
}