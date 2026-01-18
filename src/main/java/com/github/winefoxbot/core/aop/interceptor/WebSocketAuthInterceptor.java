package com.github.winefoxbot.core.aop.interceptor;

import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.core.service.webui.WebUITokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final WebUITokenService tokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // 只有在 CONNECT 阶段才进行验证
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            
            // 尝试从 STOMP 协议头中获取 Token (Native Headers)
            String token = accessor.getFirstNativeHeader("Authorization");

            // 如果头里没有，尝试从 WebSocket 握手时的 URL 参数里拿 (备选方案)
            // 某些环境下 JS WebSocket 无法设置 header，只能拼在 URL 后面 ?token=xxx
            if (StrUtil.isBlank(token)) {
                // 这里处理比较麻烦，通常通过 accessor.getSessionAttributes() 或者 user 来获取，
                // 但简单起见，我们先优先支持 Header 方式，这是 @stomp/stompjs 支持的
                log.warn("WebSocket连接未携带Authorization头");
                return null; // 返回 null 拒绝连接
            }

            // 清理 Bearer 前缀 (如果有)
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 验证 Token
            if (tokenService.validateToken(token)) {
                // 可以在这里设置 User 信息到 accessor.setUser(...) 以便后续 controller 使用
                return message;
            } else {
                log.warn("WebSocket Token 验证失败");
                return null; // 验证失败，拒绝连接
            }
        }
        
        return message;
    }
}
