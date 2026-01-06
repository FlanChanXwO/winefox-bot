package com.github.winefoxbot.plugins.pixiv.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@RestController
public class ImageController {

    private final Random random = new Random();
    private final String[] images = {"/images/1.gif", "/images/2.gif", "/images/3.gif","/images/4.gif","/images/5.gif"};
    private static final String AUTH_SESSION_KEY = "user_authorized"; // 和 ViewController 保持一致

    @GetMapping("/api/latest-image")
    public ResponseEntity<Map<String, String>> getLatestImage(HttpSession session) {
        // 检查 Session 中是否有授权标记
        Boolean isAuthorized = (Boolean) session.getAttribute(AUTH_SESSION_KEY);

        if (isAuthorized == null || !isAuthorized) {
            // 如果未授权，返回 403 Forbidden 错误
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access Denied"));
        }

        // 授权通过，正常返回图片URL
        int index = random.nextInt(images.length);
        String imageUrl = images[index] + "?t=" + System.currentTimeMillis();

        Map<String, String> response = new HashMap<>();
        response.put("url", imageUrl);

        return ResponseEntity.ok(response);
    }
}
