package com.github.winefoxbot.core.service.webui;

import com.github.winefoxbot.core.model.vo.webui.req.LoginRequest;
import com.github.winefoxbot.core.model.vo.webui.req.ResetPasswordRequest;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:27
 */
public interface WebUILoginService {
    String doLogin(LoginRequest request);

    boolean resetPassword(ResetPasswordRequest request);
}