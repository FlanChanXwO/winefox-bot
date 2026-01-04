package com.github.winefoxbot.core.service.shortlink;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    // 使用 ConcurrentHashMap 来保证线程安全
    // Key: Token字符串, Value: Token创建时间
    private static final Map<String, Instant> tokenStore = new ConcurrentHashMap<>();

    // 令牌有效期（秒），例如 5 分钟
    private static final long TOKEN_VALIDITY_SECONDS = 300; 

    /**
     * 创建一个新的、唯一的、未使用的令牌
     * @return token string
     */
    public String createToken() {
        String token = UUID.randomUUID().toString();
        tokenStore.put(token, Instant.now());
        System.out.println("创建了新令牌: " + token);
        return token;
    }

    /**
     * 验证令牌是否有效，如果有效，则消费它（使其失效）
     * @param token The token to validate
     * @return true if valid and consumed, false otherwise
     */
    public boolean consumeToken(String token) {
        if (token == null || !tokenStore.containsKey(token)) {
            System.out.println("令牌验证失败: 不存在 " + token);
            return false; // 令牌不存在
        }

        Instant creationTime = tokenStore.get(token);
        Instant now = Instant.now();

        // 检查令牌是否过期
        if (creationTime.plusSeconds(TOKEN_VALIDITY_SECONDS).isBefore(now)) {
            System.out.println("令牌验证失败: 已过期 " + token);
            tokenStore.remove(token); // 从存储中移除过期的令牌
            return false;
        }

        // 令牌有效，消费它（从map中移除，实现一次性使用）
        tokenStore.remove(token);
        System.out.println("令牌验证成功并已消费: " + token);
        return true;
    }
}
