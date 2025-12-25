package com.github.winefoxbot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-10:42
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class GroupInfo {
    private Long groupId;
    private String groupName;
    private String groupAvatarUrl;
}