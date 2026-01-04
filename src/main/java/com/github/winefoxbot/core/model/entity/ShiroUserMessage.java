package com.github.winefoxbot.core.model.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Shiro 详细訊息實體
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ShiroUserMessage extends ShiroMessage {
    private String nickname;
    private String card;
}