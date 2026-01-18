package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.model.vo.webui.req.LoginRequest;
import com.github.winefoxbot.core.model.vo.webui.req.ResetPasswordRequest;
import com.github.winefoxbot.core.service.webui.WebUILoginService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class WebUILoginController {

    private final WebUILoginService loginService;

    @PostMapping("/login")
    public Result<Void> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = loginService.doLogin(request);
        if (token != null) {
            response.setHeader("Authorization",token);
            return Result.success(null);
        } else {
            return Result.error("登录失败，用户名或密码错误");
        }
    }

    @PutMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        boolean success = loginService.resetPassword(request);
        if (success) {
            return Result.success(null);
        } else {
            return Result.error("无效的恢复码，重置密码失败");
        }
    }
}