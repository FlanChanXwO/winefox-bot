package com.github.winefoxbot.service.file;

import com.github.winefoxbot.model.dto.file.FileRecord;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 统一文件存储服务接口，用于解耦具体的文件存储实现（如本地文件系统、对象存储等）。
 */
public interface FileStorageService {

    /**
     * 将输入流写入到指定的相对路径。
     *
     * @param relativePath 存储的相对路径，例如 "images/temp/bot_icon.png"
     * @param inputStream  要写入的输入流
     * @return 存储文件的绝对路径或URI
     * @throws IOException 如果写入失败
     */
    Path writeFile(String relativePath, InputStream inputStream) throws IOException;

    /**
     * 写入文件，并设置自动过期时间。
     *
     * @param relativePath    存储的相对路径
     * @param inputStream     要写入的输入流
     * @param expireAfter     过期时长，例如 Duration.ofMinutes(5)
     * @param onDeleteCallback 文件被删除时的回调函数，参数为被删除文件的Path。可以为 null。
     * @return 存储文件的绝对路径或URI
     * @throws IOException 如果写入失败
     */
    Path writeFile(String relativePath, InputStream inputStream, Duration expireAfter, Consumer<Path> onDeleteCallback) throws IOException;

    /**
     * 读取文件内容为字节数组。
     *
     * @param path 完整的文件路径，支持 "classpath:" 前缀
     * @return 文件的字节数组
     * @throws IOException 如果文件不存在或读取失败
     */
    byte[] readFileAsBytes(String path) throws IOException;

    /**
     * 获取文件的输入流。
     *
     * @param path 完整的文件路径，支持 "classpath:" 前缀
     * @return 文件的输入流
     * @throws IOException 如果文件不存在或读取失败
     */
    InputStream readFileAsStream(String path) throws IOException;

    /**
     * 删除文件。
     *
     * @param path          要删除的文件的完整路径
     * @param afterDeleteCallback 删除成功后的回调函数，参数为被删除文件的Path。可以为 null。
     * @return 如果删除成功返回 true，否则返回 false
     * @throws IOException 如果删除过程中发生IO错误
     */
    boolean deleteFile(String path, Consumer<Path> afterDeleteCallback) throws IOException;

    /**
     * 检查文件是否存在。
     * @param path 完整文件路径
     * @return 如果存在且可读则返回 true
     */
    boolean exists(String path);

    /**
     * 列出指定目录下的所有文件路径。
     * @param directoryRelativePath 目录的相对路径
     * @return 文件路径列表
     * @throws IOException 如果目录不存在或发生错误
     */
    List<Path> listFiles(String directoryRelativePath) throws IOException;

    /**
     * 获取被服务管理的所有文件的记录。
     * @return 文件记录列表
     */
    List<FileRecord> getAllFileRecords();
}
