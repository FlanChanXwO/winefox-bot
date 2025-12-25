package com.github.winefoxbot.model.dto;

import com.mikuac.shiro.dto.event.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-20:17
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventMessage {
    private Long userId;

    private String username;

    private String rawMessage;

    private Event event;
}