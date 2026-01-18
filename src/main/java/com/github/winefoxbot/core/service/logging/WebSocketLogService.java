package com.github.winefoxbot.core.service.logging;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author FlanChan
 */
@Service
@RequiredArgsConstructor
public class WebSocketLogService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Deque<String> logHistoryBuffer = new ConcurrentLinkedDeque<>();

    private static final int MAX_HISTORY_SIZE = 200;

    public void sendLog(String logMessage) {
        logHistoryBuffer.offerLast(logMessage); // 存入队尾
        if (logHistoryBuffer.size() > MAX_HISTORY_SIZE) {
            logHistoryBuffer.pollFirst(); // 移除队头
        }
        // 广播给所有在线用户（只发这一条新的）
        messagingTemplate.convertAndSend("/topic/logs", logMessage);
    }

    // 新增：给 Controller 提供获取历史记录的方法
    public Collection<String> getHistory() {
        // 返回一个不可变的副本，防止并发修改异常
        return List.copyOf(logHistoryBuffer);
    }
}
