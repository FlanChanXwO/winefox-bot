package com.github.winefoxbot.service.file.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.winefoxbot.model.dto.file.FileRecord;
import com.github.winefoxbot.service.file.FileStorageService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class LocalStorageService implements FileStorageService {

    private final Path storageBasePath; // 存储根目录，由工厂注入
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Path recordFilePath; // 记录文件的路径

    // 使用 ConcurrentHashMap 来安全地管理文件记录
    private final ConcurrentMap<String, FileRecord> fileRecords = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            if (Files.notExists(storageBasePath)) {
                Files.createDirectories(storageBasePath);
                log.info("Local storage base directory created at: {}", storageBasePath.toAbsolutePath());
            }
            loadRecords(); // 从文件加载记录
        } catch (IOException e) {
            log.error("Failed to initialize LocalStorageService", e);
            throw new IllegalStateException("Could not initialize storage directory or records", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        saveRecords(); // 应用关闭时保存记录
    }

    @Override
    public Path writeFile(String relativePath, InputStream inputStream) throws IOException {
        return writeFile(relativePath, inputStream, null, null);
    }

    @Override
    public Path writeFile(String relativePath, InputStream inputStream, Duration expireAfter, Consumer<Path> onDeleteCallback) throws IOException {
        // 解析路径，防止路径遍历攻击
        Path finalPath = resolveSecurely(relativePath);

        // 确保父目录存在
        Files.createDirectories(finalPath.getParent());

        try (InputStream is = inputStream) {
            Files.copy(is, finalPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("File written to: {}", finalPath.toAbsolutePath());

        // 创建并添加记录
        Instant now = Instant.now();
        Instant expireTime = (expireAfter != null) ? now.plus(expireAfter) : null;
        FileRecord record = new FileRecord(finalPath.toAbsolutePath().toString(), now, expireTime, onDeleteCallback);
        fileRecords.put(record.getAbsolutePath(), record);
        
        saveRecords(); // 实时保存记录
        
        // 触发回调
        if (onDeleteCallback != null) {
            log.debug("Write operation successful for {}, callback is registered.", finalPath);
        }

        return finalPath;
    }

    @Override
    public byte[] readFileAsBytes(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            return new ClassPathResource(path.substring("classpath:".length())).getInputStream().readAllBytes();
        }
        return Files.readAllBytes(Paths.get(path));
    }

    @Override
    public InputStream readFileAsStream(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            return new ClassPathResource(path.substring("classpath:".length())).getInputStream();
        }
        return Files.newInputStream(Paths.get(path));
    }

    @Override
    public boolean deleteFile(String path, Consumer<Path> afterDeleteCallback) throws IOException {
        Path filePath = Paths.get(path);
        boolean deleted = Files.deleteIfExists(filePath);
        if (deleted) {
            log.info("File deleted successfully: {}", path);
            fileRecords.remove(path); // 从记录中移除
            saveRecords();
            if (afterDeleteCallback != null) {
                afterDeleteCallback.accept(filePath);
            }
        }
        return deleted;
    }

    @Override
    public boolean exists(String path) {
        if (path.startsWith("classpath:")) {
            return new ClassPathResource(path.substring("classpath:".length())).exists();
        }
        Path filePath = Paths.get(path);
        return Files.exists(filePath) && Files.isReadable(filePath);
    }

    @Override
    public List<Path> listFiles(String directoryRelativePath) throws IOException {
        Path dirPath = resolveSecurely(directoryRelativePath);
        if (!Files.isDirectory(dirPath)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream.collect(Collectors.toList());
        }
    }

    @Override
    public List<FileRecord> getAllFileRecords() {
        return List.copyOf(fileRecords.values());
    }
    
    // 定时任务，每分钟检查一次过期文件
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredFiles() {
        log.debug("Running scheduled task to clean up expired files...");
        Instant now = Instant.now();
        fileRecords.values().stream()
                .filter(record -> record.getExpireTime() != null && record.getExpireTime().isBefore(now))
                .forEach(record -> {
                    try {
                        log.info("File expired: {}. Deleting...", record.getAbsolutePath());
                        // 使用带回调的删除方法
                        deleteFile(record.getAbsolutePath(), record.getOnDeleteCallback());
                    } catch (IOException e) {
                        log.error("Error deleting expired file: {}", record.getAbsolutePath(), e);
                    }
                });
    }

    private void saveRecords() {
        try {
            // 将 ConcurrentHashMap 的值集合转换为普通 List 进行序列化
            List<FileRecord> recordsToSave = List.copyOf(fileRecords.values());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recordsToSave);
            Files.writeString(recordFilePath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("File records saved to {}", recordFilePath);
        } catch (IOException e) {
            log.error("Failed to save file records", e);
        }
    }

    private void loadRecords() {
        if (Files.exists(recordFilePath)) {
            try {
                String json = Files.readString(recordFilePath);
                if (!json.isBlank()) {
                    List<FileRecord> loadedRecords = objectMapper.readValue(json, new TypeReference<>() {});
                    // 重新填充到 ConcurrentMap 中，注意回调会丢失
                    loadedRecords.forEach(record -> {
                        record.setOnDeleteCallback(path -> log.warn("Callback for {} was lost on restart.", path));
                        fileRecords.put(record.getAbsolutePath(), record);
                    });
                    log.info("Loaded {} file records from {}", loadedRecords.size(), recordFilePath);
                }
            } catch (IOException e) {
                log.error("Failed to load file records", e);
            }
        }
    }

    /**
     * 安全地解析相对路径，防止目录遍历攻击。
     */
    private Path resolveSecurely(String relativePath) {
        Path inputPath = Paths.get(relativePath).normalize();
        if (inputPath.isAbsolute() || inputPath.startsWith("..")) {
            throw new IllegalArgumentException("Invalid relative path: " + relativePath);
        }
        return storageBasePath.resolve(inputPath).normalize();
    }
}
