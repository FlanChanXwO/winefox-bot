package com.github.winefoxbot.plugins.pixiv.controller;

import com.github.winefoxbot.core.service.shortlink.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Controller
public class ViewController {

    @Autowired
    private TokenService tokenService;

    // session中用于授权的键
    private static final String AUTH_SESSION_KEY = "user_authorized";

    /**
     * 步骤1：后台调用此接口生成带令牌的URL
     * 你可以用这个接口来生成你的二维码内容
     * 比如，生成后返回 {"url": "http://localhost:8080/show?token=xxxx-xxxx-xxxx"}
     */
    @GetMapping("/generate-link")
    @ResponseBody
    public Map<String, String> generateLink() {
        String token = tokenService.createToken();
        String url = "/show?token=" + token; // 实际应为完整域名
        return Map.of("url", url);
    }

    /**
     * 步骤2：用户通过二维码访问此链接
     */
    @GetMapping("/show")
    public String showPage(@RequestParam(required = false) String token, HttpSession session) {
        // 验证令牌
        if (tokenService.consumeToken(token)) {
            // 验证通过，在session中设置一个标记
            session.setAttribute(AUTH_SESSION_KEY, true);
            // 返回受保护的HTML页面
            return "short-preview/index.html";
        } else {
            // 验证失败，返回错误页面
            return "short-preview/error.html";
        }
    }
}
