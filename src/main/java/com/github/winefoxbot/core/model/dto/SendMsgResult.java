package com.github.winefoxbot.core.model.dto;

import com.mikuac.shiro.dto.action.common.ActionRaw;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-04-0:03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMsgResult {
    private boolean success;

    private String status;

    private Integer messageId;

    private Object data;

    // 新增字段：专门用于存储群文件上传结果
    private ActionRaw groupFileInfo;

    public SendMsgResult(boolean success, String status, Integer messageId) {
        this.success = success;
        this.status = status;
        this.messageId = messageId;
    }

    public SendMsgResult(boolean success, String status) {
        this.success = success;
        this.status = status;
    }

    public SendMsgResult(boolean success) {
        this.success = success;
    }
}