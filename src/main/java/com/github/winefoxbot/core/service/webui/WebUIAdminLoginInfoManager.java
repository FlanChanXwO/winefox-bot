package com.github.winefoxbot.core.service.webui;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.config.webui.WebUIProperties;
import com.github.winefoxbot.core.model.entity.WebUIAdmin;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
@Slf4j
public class WebUIAdminLoginInfoManager {

    private final Path dataFilePath;
    private final ObjectMapper objectMapper;
    private final WebUIProperties defaultProperties;
    private final PasswordEncoder passwordEncoder;


    
    // 内存中的当前用户数据
    private volatile WebUIAdmin currentUser;
    // 读写锁，防止并发读写文件出错
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public WebUIAdminLoginInfoManager(ObjectMapper objectMapper, WebUIProperties defaultProperties, PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dataFilePath = Path.of("./config/webui/admin_user.json");
        this.objectMapper = objectMapper;
        this.defaultProperties = defaultProperties;
    }

    @PostConstruct
    public void init() {
        File file = dataFilePath.toFile();
        if (file.exists()) {
            loadFromFile();
        } else {
            try {
                Files.createDirectories(dataFilePath.getParent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // 文件不存在，使用配置文件中的默认值初始化
            log.info("No persistent user file found. Initializing with default config.");
            this.currentUser = new WebUIAdmin(
                defaultProperties.getAdmin().getUsername(),
                passwordEncoder.encode(defaultProperties.getAdmin().getPassword())
            );
            // 立即保存一份到文件，确保后续以此为准
            saveToFile(this.currentUser);
        }
    }

    public WebUIAdmin getCurrentUser() {
        lock.readLock().lock();
        try {
            return currentUser;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void updatePassword(String newPassword) {
        lock.writeLock().lock();
        try {
            // 保持用户名不变，只更新密码
            WebUIAdmin updatedUser = new WebUIAdmin(currentUser.getUsername(), passwordEncoder.encode(newPassword));
            saveToFile(updatedUser); // 持久化
            this.currentUser = updatedUser; // 更新内存
            log.info("Admin password updated and persisted to {}", dataFilePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void loadFromFile() {
        try {
            this.currentUser = objectMapper.readValue(dataFilePath.toFile(), WebUIAdmin.class);
            log.info("Loaded admin user [{}] from file", currentUser.getUsername());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load user data from file", e);
        }
    }

    private void saveToFile(WebUIAdmin data) {
        try {
            // 确保父目录存在
            Files.createDirectories(dataFilePath.getParent());
            // 写入文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFilePath.toFile(), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save user data to file", e);
        }
    }
}
