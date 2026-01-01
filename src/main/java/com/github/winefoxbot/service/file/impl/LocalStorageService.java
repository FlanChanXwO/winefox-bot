package com.github.winefoxbot.service.file.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.model.dto.file.FileRecord;
import com.github.winefoxbot.service.file.FileStorageService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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

    private final Path storageBasePath;
    private final ObjectMapper objectMapper;
    private final Path recordFilePath;

    private final ConcurrentMap<String, FileRecord> fileRecords = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            if (Files.notExists(storageBasePath)) {
                Files.createDirectories(storageBasePath);
                log.info("Local storage base directory created at: {}", storageBasePath.toAbsolutePath());
            }
            loadAndCleanupRecords(); // 启动时加载并清理
        } catch (IOException e) {
            log.error("Failed to initialize LocalStorageService", e);
            throw new IllegalStateException("Could not initialize storage directory or records", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        saveRecords(); // 应用关闭时保存记录~
    }

    @Override
    public byte[] getFileByCacheKey(String cacheKey) {
        // 将缓存键解析为文件的绝对路径
        Path filePath = resolveSecurely(cacheKey);

        // 检查文件记录是否存在且未过期
        FileRecord record = fileRecords.get(filePath.toAbsolutePath().toUri().toString());
        if (record == null) {
            // 内存中没有记录，可能是应用重启后还没加载，或者根本不存在
            // 我们可以直接检查文件是否存在，但不检查过期时间，因为记录没了
            if (Files.notExists(filePath)) {
                return null;
            }
        } else {
            // 如果有记录，检查是否过期
            Instant now = Instant.now();
            if (record.getExpireTime() != null && record.getExpireTime().isBefore(now)) {
                log.info("Cache for key '{}' expired at {}. Returning null.", cacheKey, record.getExpireTime());
                // 可以在这里触发一个异步删除任务，或者依赖定时清理任务
                return null;
            }
        }

        // 文件存在且（据记录）未过期，尝试读取
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.warn("Failed to read file for cache key '{}' at path '{}', even though it was expected to exist.", cacheKey, filePath, e);
            // 文件可能在检查后被外部删除，或者出现读取权限问题
            return null;
        }
    }

    @Override
    public void saveFileByCacheKey(String cacheKey, byte[] data, Duration expireAfter) {
        // 使用 cacheKey 作为相对路径，利用已有的 writeFile 方法来处理文件的写入和注册
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            writeFile(cacheKey, inputStream, expireAfter, null);
            log.info("File for cache key '{}' saved successfully.", cacheKey);
        } catch (IOException e) {
            log.error("Failed to save file for cache key '{}'.", cacheKey, e);
            // 根据业务需求，这里可以抛出自定义的运行时异常
        }
    }


    @Override
    public Path writeFile(String relativePath, InputStream inputStream) throws IOException {
        return writeFile(relativePath, inputStream, null, null);
    }

    @Override
    public Path writeFile(String relativePath, InputStream inputStream, Duration expireAfter, Consumer<Path> onDeleteCallback) throws IOException {
        Path finalPath = resolveSecurely(relativePath);
        Files.createDirectories(finalPath.getParent());

        try (InputStream is = inputStream) {
            Files.copy(is, finalPath, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("File written to: {}", finalPath.toAbsolutePath());
        registerFile(finalPath, expireAfter, onDeleteCallback);
        return finalPath;
    }

    @Override
    public void registerFile(Path absolutePath, Duration expireAfter, Consumer<Path> onDeleteCallback) {
        Instant now = Instant.now();
        Instant expireTime = (expireAfter != null) ? now.plus(expireAfter) : null;

        // [!!!! 关键修正 !!!!] 将 Path 转换为 URI
        URI fileUri = absolutePath.toAbsolutePath().toUri();

        // 注意：这里的 key 仍然使用 string, 但 record 内部使用 URI
        FileRecord record = new FileRecord(fileUri, now, expireTime, onDeleteCallback);
        fileRecords.put(fileUri.toString(), record); // 使用 URI 的字符串形式作为 key

        // 之前建议的优化：移除这里的 saveRecords()，或者保持原样看是否能解决问题先
        saveRecords();

        if (onDeleteCallback != null) {
            log.debug("File {} registered with a callback.", fileUri);
        }
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
    public boolean deleteFile(String pathString, Consumer<Path> afterDeleteCallback) throws IOException {
        Path filePath;
        try {
            // 尝试将输入视为 URI
            filePath = Paths.get(new URI(pathString));
        } catch (Exception e) {
            // 如果不是有效的URI，则按普通路径处理
            filePath = Paths.get(pathString);
        }


        boolean deleted = Files.deleteIfExists(filePath);
        if (deleted) {
            log.info("File deleted successfully: {}", pathString);
            if (afterDeleteCallback != null) {
                afterDeleteCallback.accept(filePath);
            }
        }
        saveRecords(); // 保存记录的变更
        return deleted;
    }

    @Override
    public boolean deleteDirectory(Path directoryPath) throws IOException {
        if (!Files.isDirectory(directoryPath)) {
            return false;
        }

        try (Stream<Path> walk = Files.walk(directoryPath)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            // 从记录中移除文件
                            if (fileRecords.remove(path.toString()) != null) {
                                log.info("Removed record and file during directory cleanup: {}", path);
                            }
                        } catch (IOException e) {
                            log.error("Failed to delete path during directory cleanup: {}", path, e);
                        }
                    });
        }
        saveRecords();
        log.info("Directory and all its contents deleted successfully: {}", directoryPath);
        return true;
    }

    @Override
    public boolean deleteDirectory(String directoryRelativePath) throws IOException {
        Path directoryPath = resolveSecurely(directoryRelativePath);
        if (!Files.isDirectory(directoryPath)) {
            return false; // 目录不存在，无需操作
        }

        boolean recordsChanged = false;
        try (Stream<Path> walk = Files.walk(directoryPath)) {
            // 需要收集路径后操作，避免在Stream中修改集合
            List<Path> pathsToDelete = walk.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : pathsToDelete) {
                try {
                    if (!Files.isDirectory(path)) {
                        // 从记录中移除文件记录
                        if (fileRecords.remove(path.toString()) != null) {
                            log.info("Removed record for file during directory cleanup: {}", path);
                            recordsChanged = true;
                        }
                    }
                    Files.delete(path);
                } catch (IOException e) {
                    log.error("Failed to delete path during directory cleanup: {}", path, e);
                }
            }
        }

        if (recordsChanged) {
            saveRecords(); // 仅当记录发生变化时，在所有删除操作完成后保存一次
        }

        log.info("Directory and all its contents deleted successfully: {}", directoryPath);
        return true;
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

    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void cleanupExpiredFiles() {
        log.debug("Running scheduled task to clean up expired files...");
        Instant now = Instant.now();
        boolean changed = false;
        for (FileRecord record : fileRecords.values()) {
            if (record.getExpireTime() != null && record.getExpireTime().isBefore(now)) {
                try {
                    log.info("File expired: {}. Deleting...", record.getAbsolutePath());
                    deleteFile(record.getAbsolutePath().getPath(), record.getOnDeleteCallback());
                    changed = true;
                } catch (IOException e) {
                    log.error("Error deleting expired file: {}", record.getAbsolutePath(), e);
                }
            }
        }
        if (changed) {
            log.info("Expired file cleanup finished, some files were deleted.");
        } else {
            log.debug("Expired file cleanup finished, no files to delete.");
        }
    }

    private void saveRecords() {
        try {
            List<FileRecord> recordsToSave = List.copyOf(fileRecords.values());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recordsToSave);
            Files.writeString(recordFilePath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("File records saved to {}", recordFilePath);
        } catch (IOException e) {
            log.error("Failed to save file records", e);
        }
    }

    private void loadAndCleanupRecords() {
        if (Files.exists(recordFilePath)) {
            try {
                String json = Files.readString(recordFilePath);
                if (json.isBlank()) {
                    return;
                }
                // 使用您已经定义的 FileRecord 类型
                List<FileRecord> loadedRecords = objectMapper.readValue(json, new TypeReference<List<FileRecord>>() {});
                Instant now = Instant.now();
                boolean recordsChanged = false;

                for (FileRecord record : loadedRecords) {
                    try {
                        Path path = Paths.get(record.getAbsolutePath());
                        boolean fileExists = Files.exists(path);
                        boolean isExpired = record.getExpireTime() != null && record.getExpireTime().isBefore(now);

                        // 条件1: 文件不存在了
                        if (!fileExists) {
                            log.info("File not found. Removing stale record on startup: {}", record.getAbsolutePath());
                            recordsChanged = true; // 标记记录需要更新
                        }
                        // 条件2: 文件存在但已过期
                        else if (isExpired) {
                            try {
                                Files.delete(path);
                                log.info("Deleted expired file on startup: {}", path);
                            } catch (IOException e) {
                                log.error("Failed to delete expired file on startup: {}", path, e);
                            }
                            log.info("File expired. Removing record on startup: {}", record.getAbsolutePath());
                            recordsChanged = true; // 标记记录需要更新
                        }
                        // 条件3: 文件存在且未过期 -> 是有效文件
                        else {
                            // 重新填充到内存 map
                            record.setOnDeleteCallback(p -> log.warn("Callback for {} was lost on restart.", p));
                            fileRecords.put(record.getAbsolutePath().toString(), record);
                        }
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid path URI found in records, skipping: {}", record.getAbsolutePath(), e);
                    }
                }

                log.info("Loaded {} valid file records from {}. Stale/expired records cleaned up.", fileRecords.size(), recordFilePath);

                if (recordsChanged) {
                    saveRecords(); // 如果有记录被清理，立即重写记录文件
                }

            } catch (IOException e) {
                log.error("Failed to load and cleanup file records", e);
            }
        }
    }

    private Path resolveSecurely(String relativePath) {
        Path inputPath = Paths.get(relativePath).normalize();
        if (inputPath.isAbsolute() || inputPath.startsWith("..")) {
            throw new IllegalArgumentException("Invalid relative path: " + relativePath);
        }
        return storageBasePath.resolve(inputPath).normalize();
    }
}
