package com.github.winefoxbot.core.model.dto;

import com.github.winefoxbot.core.model.enums.common.GroupMemberRole;
import lombok.Data;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-16:15
 */
@Data
public class GroupMemberInfo {
    private Long userId;
    private Long groupId;
    private String nickname;
    private String card;
    private GroupMemberRole role;
}