package com.github.winefoxbot.service.file.impl;


import com.github.winefoxbot.model.dto.file.FileRecord;
import com.github.winefoxbot.service.file.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * 对象存储服务的占位实现。
 * 所有方法均抛出 UnsupportedOperationException，待未来具体实现。
 */
public class ObjectStorageService implements FileStorageService {
    private static final String NOT_IMPLEMENTED_MSG = "Object storage is not implemented yet.";

    @Override
    public Path getFilePathByCacheKey(String cacheKey) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public Path saveFileByCacheKey(String cacheKey, InputStream inputStream, Duration expireAfter) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public byte[] getFileByCacheKey(String cacheKey) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public void saveFileByCacheKey(String cacheKey, byte[] data, Duration expireAfter) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public Path writeFile(String relativePath, InputStream inputStream) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }
    @Override
    public Path writeFile(String relativePath, InputStream inputStream, Duration expireAfter, Consumer<Path> onDeleteCallback) throws IOException {
         throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public void registerFile(Path absolutePath, Duration expireAfter, Consumer<Path> onDeleteCallback) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public boolean deleteDirectory(Path directoryPath) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public boolean deleteDirectory(String directoryRelativePath) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public byte[] readFileAsBytes(String path) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public InputStream readFileAsStream(String path) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public boolean deleteFile(String path, Consumer<Path> afterDeleteCallback) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public boolean exists(String path) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public List<Path> listFiles(String directoryRelativePath) throws IOException {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public List<FileRecord> getAllFileRecords() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

}
