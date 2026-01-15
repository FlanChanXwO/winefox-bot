package com.github.winefoxbot.core.service.logging;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * @author FlanChan
 */
@Service
@RequiredArgsConstructor
public class WebSocketLogService {

    private @Autowired SimpMessagingTemplate messagingTemplate;

    /**
     * 发送日志消息到WebSocket主题.
     * @param message 日志消息
     */
    public void sendLog(String message) {
        messagingTemplate.convertAndSend("/topic/logs", message);
    }
}
