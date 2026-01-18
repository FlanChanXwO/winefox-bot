package com.github.winefoxbot.core.service.webui;

public interface WebUITokenService {
    /**
     * 校验 Token 是否有效
     */
    boolean validateToken(String token);
    
}
