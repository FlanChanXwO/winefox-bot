package com.github.winefoxbot.core.model.dto;

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
public class TextReply {
    private String text;
    private byte [] picture;
}