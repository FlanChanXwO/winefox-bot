package com.github.winefoxbot.model.dto;

import com.mikuac.shiro.dto.event.Event;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-20:20
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class GroupEventMessage extends EventMessage {
    private Long groupId;

    public GroupEventMessage(Long userId, String username, String rawMessage, Event event, Long groupId) {
        super(userId, username,rawMessage,event);
        this.groupId = groupId;
    }
}