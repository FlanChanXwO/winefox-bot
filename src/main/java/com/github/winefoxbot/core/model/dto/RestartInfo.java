package com.github.winefoxbot.core.model.dto;

import com.github.winefoxbot.core.model.enums.common.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于在重启过程中持久化重启信息的 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestartInfo {
    private MessageType messageType;
    // 目标ID (群号或QQ号)
    private Long targetId;
    // 成功后要发送的消息
    private String successMessage;
    // 发起重启时的时间戳 (毫秒)
    private long startTimeMillis; // <-- 新增字段

}
