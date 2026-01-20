package com.github.winefoxbot.core.service.webui.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.github.winefoxbot.core.config.webui.WebUIProperties;
import com.github.winefoxbot.core.model.entity.WebUIAdmin;
import com.github.winefoxbot.core.model.vo.webui.req.login.LoginRequest;
import com.github.winefoxbot.core.model.vo.webui.req.login.ResetPasswordRequest;
import com.github.winefoxbot.core.service.webui.WebUIAdminLoginInfoManager;
import com.github.winefoxbot.core.service.webui.WebUILoginService;
import com.github.winefoxbot.core.service.webui.WebUITokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebUILoginServiceImpl implements WebUILoginService, WebUITokenService {

    private final WebUIAdminLoginInfoManager loginInfoManager;
    private final PasswordEncoder passwordEncoder;
    private final WebUIProperties webUIProperties;

    private byte[] secretKeyBytes;

    @PostConstruct
    public void init() {
        // Hutool 接受 byte[] 作为密钥
        String secret = webUIProperties.getJwtSecret();
        if (secret == null || secret.length() < 16) {
            secret = "WineFoxBotDefaultSecretKey123456"; // 兜底
        }
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String doLogin(LoginRequest request) {
        WebUIAdmin currentUser = loginInfoManager.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        // 1. 校验密码 (必须用 matches)
        if (currentUser.getUsername().equals(request.username()) && passwordEncoder.matches(request.password(), currentUser.getPassword())) {
            // 2. 使用 Hutool 生成 Token
            return createToken(currentUser.getUsername());
        }
        return null;
    }

    @Override
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            if (!JWTUtil.verify(token, secretKeyBytes)) {
                return false;
            }
            JWT jwt = JWTUtil.parseToken(token);
            jwt.setSigner(JWTSignerUtil.hs256(secretKeyBytes));
            return jwt.validate(0);
        } catch (Exception e) {
            log.warn("JWT Check Failed: {}", e.getMessage());
            return false;
        }
    }


    @Override
    public boolean resetPassword(ResetPasswordRequest request) {
        String recoveryCode = webUIProperties.getRecoveryCode();
        if (recoveryCode == null || !recoveryCode.equals(request.recoverToken())) {
            return false;
        }
        // 重置密码记得加密
        loginInfoManager.updatePassword(passwordEncoder.encode(request.newPassword()));
        return true;
    }

    /**
     * Hutool 生成 JWT
     */
    private String createToken(String username) {
        DateTime now = DateTime.now();
        DateTime newTime = now.offsetNew(DateField.HOUR, 24); // 24小时有效期

        Map<String, Object> payload = new HashMap<>();
        // 签发时间
        payload.put(JWTPayload.ISSUED_AT, now);
        // 过期时间
        payload.put(JWTPayload.EXPIRES_AT, newTime);
        // 生效时间
        payload.put(JWTPayload.NOT_BEFORE, now);
        // 主题 (存用户名)
        payload.put(JWTPayload.SUBJECT, username);

        return JWTUtil.createToken(payload, secretKeyBytes);
    }
}
