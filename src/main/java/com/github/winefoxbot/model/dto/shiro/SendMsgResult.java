package com.github.winefoxbot.model.dto.shiro;

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