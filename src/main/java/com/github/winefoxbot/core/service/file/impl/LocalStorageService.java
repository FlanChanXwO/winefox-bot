package com.github.winefoxbot.core.service.file.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.core.model.dto.FileRecord;
import com.github.winefoxbot.core.service.file.FileStorageService;
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
import java.util.*;
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
    public Path getFilePathByCacheKey(String cacheKey) {
        Path filePath = resolveSecurely(cacheKey);

        // 首先检查文件是否存在于文件系统
        if (Files.notExists(filePath)) {
            return null;
        }

        // 然后检查文件记录是否存在且未过期
        FileRecord record = fileRecords.get(filePath.toAbsolutePath().toUri().toString());
        if (record != null) {
            Instant now = Instant.now();
            if (record.getExpireTime() != null && record.getExpireTime().isBefore(now)) {
                log.info("Cache for key '{}' expired at {}. Returning null path.", cacheKey, record.getExpireTime());
                // 依赖定时任务清理，这里只返回null
                return null;
            }
        }
        // 如果记录不存在但文件存在（可能重启后），暂时认为有效
        return filePath;
    }

    @Override
    public Path saveFileByCacheKey(String cacheKey, InputStream inputStream, Duration expireAfter) throws IOException {
        // 直接复用 writeFile 方法
        return writeFile(cacheKey, inputStream, expireAfter, null);
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
                List<FileRecord> loadedRecords = objectMapper.readValue(json, new TypeReference<>() {});
                Instant now = Instant.now();
                boolean recordsChanged = false;

                // 用于收集被删除文件所在的父目录，以便后续检查是否为空
                Set<Path> parentDirectoriesToCheck = new HashSet<>();

                for (FileRecord record : loadedRecords) {
                    try {
                        Path path = Paths.get(record.getAbsolutePath());
                        Path parentDir = path.getParent(); // 获取父目录

                        boolean fileExists = Files.exists(path);
                        boolean isExpired = record.getExpireTime() != null && record.getExpireTime().isBefore(now);

                        // 条件1: 文件不存在了
                        if (!fileExists) {
                            log.info("File not found. Removing stale record on startup: {}", record.getAbsolutePath());
                            recordsChanged = true;
                            if (parentDir != null) {
                                parentDirectoriesToCheck.add(parentDir);
                            }
                        }
                        // 条件2: 文件存在但已过期
                        else if (isExpired) {
                            try {
                                Files.delete(path);
                                log.info("Deleted expired file on startup: {}", path);
                                if (parentDir != null) {
                                    parentDirectoriesToCheck.add(parentDir);
                                }
                            } catch (IOException e) {
                                log.error("Failed to delete expired file on startup: {}", path, e);
                            }
                            log.info("File expired. Removing record on startup: {}", record.getAbsolutePath());
                            recordsChanged = true;
                        }
                        // 条件3: 文件存在且未过期 -> 是有效文件
                        else {
                            record.setOnDeleteCallback(p -> log.warn("Callback for {} was lost on restart.", p));
                            fileRecords.put(record.getAbsolutePath().toString(), record);
                        }
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid path URI found in records, skipping: {}", record.getAbsolutePath(), e);
                    }
                }

                log.info("Loaded {} valid file records from {}. Stale/expired records cleaned up.", fileRecords.size(), recordFilePath);

                // 在保存记录之前，检查并删除空的父目录
                if (!parentDirectoriesToCheck.isEmpty()) {
                    log.info("Checking {} directories for potential cleanup on startup.", parentDirectoriesToCheck.size());
                    for (Path dir : parentDirectoriesToCheck) {
                        cleanupEmptyParentDirectories(dir);
                    }
                }

                if (recordsChanged) {
                    // 我们只保存有效的记录，所以这里保存的是 fileRecords 的内容
                    saveRecords();
                }

            } catch (IOException e) {
                log.error("Failed to load and cleanup file records", e);
            }
        }
    }

    /**
     * 定时任务，周期性地清理无效的文件记录和对应的物理文件。
     * 这确保了即使应用程序长时间运行，过期的和已被外部删除的文件记录也能被正确处理。
     */
    @Scheduled(fixedRate = 3600000) // 每小时检查一次 (3600 * 1000 ms)
    public void clearInvalidFiles() {
        log.info("Starting scheduled cleanup of file records...");
        Instant now = Instant.now();
        boolean recordsChanged = false;

        // 用于收集被删除文件所在的父目录，以便后续检查是否为空
        Set<Path> parentDirectoriesToCheck = new HashSet<>();

        // 使用迭代器遍历 ConcurrentHashMap 是线程安全的做法
        Iterator<Map.Entry<String, FileRecord>> iterator = fileRecords.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, FileRecord> entry = iterator.next();
            FileRecord record = entry.getValue();
            Path path = Paths.get(record.getAbsolutePath());

            // 获取文件的父目录
            Path parentDir = path.getParent();

            boolean fileExists = Files.exists(path);
            boolean isExpired = record.getExpireTime() != null && record.getExpireTime().isBefore(now);

            // 条件1: 文件在外部被删除了，但记录还存在
            if (!fileExists) {
                log.info("File not found during scheduled check. Removing stale record: {}", record.getAbsolutePath());
                iterator.remove(); // 从 map 中安全地移除当前记录
                recordsChanged = true;
                if (parentDir != null) {
                    parentDirectoriesToCheck.add(parentDir);
                }
            }
            // 条件2: 文件存在但已过期
            else if (isExpired) {
                log.info("File expired during scheduled check. Removing record: {}", record.getAbsolutePath());
                try {
                    Files.delete(path);
                    log.info("Deleted expired file: {}", path);
                    if (parentDir != null) {
                        parentDirectoriesToCheck.add(parentDir);
                    }
                } catch (IOException e) {
                    log.error("Failed to delete expired file during scheduled check: {}", path, e);
                }
                iterator.remove(); // 无论删除成功与否，都移除记录
                recordsChanged = true;
            }
            // 文件有效，无需处理
        }

        // 清理完成后，检查并删除空的父目录
        if (!parentDirectoriesToCheck.isEmpty()) {
            log.info("Checking {} directories for potential cleanup.", parentDirectoriesToCheck.size());
            for (Path dir : parentDirectoriesToCheck) {
                cleanupEmptyParentDirectories(dir);
            }
        }

        if (recordsChanged) {
            log.info("File records were changed during scheduled cleanup. Saving updated records to disk.");
            saveRecords(); // 如果有记录被清理，则将变更持久化到文件
        } else {
            log.info("Scheduled cleanup finished. No invalid file records found.");
        }
    }

    /**
     * 递归地清理空目录。
     * 它会检查给定目录是否为空，如果是，则删除它，并继续检查其父目录。
     *
     * @param directory 要检查的目录路径
     */
    private void cleanupEmptyParentDirectories(Path directory) {
        // 确保我们不会意外地删除根存储目录或其之上的任何目录
        if (directory == null || !directory.startsWith(storageBasePath) || directory.equals(storageBasePath)) {
            return;
        }

        try {
            // 只有当目录存在且为空时才删除
            if (Files.isDirectory(directory) && isDirEmpty(directory)) {
                try {
                    Files.delete(directory);
                    log.info("Cleaned up empty directory: {}", directory);
                    // 递归检查父目录
                    cleanupEmptyParentDirectories(directory.getParent());
                } catch (DirectoryNotEmptyException e) {
                    // 并发情况：在检查和删除之间有新文件被创建，这没问题，记录一下即可
                    log.warn("Directory {} was not empty upon deletion attempt, likely due to concurrent operations.", directory);
                } catch (IOException e) {
                    log.error("Failed to delete empty directory: {}", directory, e);
                }
            }
        } catch (IOException e) {
            log.error("Error while checking if directory is empty: {}", directory, e);
        }
    }

    /**
     * 检查目录是否为空。
     * @param directory 目录路径
     * @return 如果目录为空或不存在，则返回 true
     * @throws IOException 如果发生 I/O 错误
     */
    private boolean isDirEmpty(final Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            // stream.findAny().isEmpty() 在 Java 11+ 中可用
            // 对于 Java 8, 使用 stream.findFirst().isPresent() 的反义
            return stream.findFirst().isEmpty();
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
