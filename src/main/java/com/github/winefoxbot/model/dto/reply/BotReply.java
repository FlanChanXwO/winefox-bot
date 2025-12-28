package com.github.winefoxbot.model.dto.reply;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-20:59
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotReply {
    private String text;
    private byte [] picture;
}