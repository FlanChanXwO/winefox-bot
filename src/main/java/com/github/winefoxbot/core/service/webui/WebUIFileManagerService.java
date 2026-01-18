package com.github.winefoxbot.core.service.webui;

import com.github.winefoxbot.core.model.vo.webui.resp.FileItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class WebUIFileManagerService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");


    // 定义支持编辑的文件后缀白名单 (尽量使用 Set 提高查询速度)
    private static final Set<String> EDITABLE_EXTENSIONS = Set.of(
            "txt", "md", "json", "xml", "yml", "yaml", "properties", "ini", "conf","cfg","csv","go", "cpp", "c",
            "java", "py", "js", "ts", "html", "css", "scss", "sql", "sh", "bat", "log"
    );


    /**
     * 获取文件列表
     * @param pathString 目标路径，如果为空则返回系统根目录（Windows盘符或Linux根）
     */
    public List<FileItemResponse> listFiles(String pathString) throws IOException {
        // 如果路径为空，列出系统根目录 (例如 Windows 的 C:\, D:\)
        if (!StringUtils.hasText(pathString)) {
            File[] roots = File.listRoots();
            return Arrays.stream(roots)
                    .map(file -> new FileItemResponse(
                            String.valueOf(file.getAbsolutePath().hashCode()),
                            file.getAbsolutePath(),
                            file.getAbsolutePath(),
                            "-",
                            formatSize(file.getTotalSpace()), // 显示总空间作为代替
                            "folder",
                            0L,
                            !file.isDirectory() && isEditableFile(file.getName())
                    )).toList();
        }

        Path path = Paths.get(pathString);
        if (!Files.exists(path)) {
            throw new RuntimeException("路径不存在: " + pathString);
        }

        try (Stream<Path> stream = Files.list(path)) {
            return stream.map(p -> {
                try {
                    // 获取文件属性
                    File file = p.toFile();
                    String fileName = file.getName();
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    boolean isDir = attrs.isDirectory();
                    boolean editable = !isDir && isEditableFile(fileName);
                    
                    String formattedDate = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault())
                            .format(DATE_FORMATTER);

                    return new FileItemResponse(
                            String.valueOf(p.toAbsolutePath().hashCode()), // 简单的ID生成
                            p.getFileName().toString(),
                            p.toAbsolutePath().toString(),
                            formattedDate,
                            isDir ? "-" : formatSize(attrs.size()),
                            isDir ? "folder" : "file",
                            attrs.size(),
                            editable
                    );
                } catch (Exception e) {
                    // 忽略无权限读取的文件
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull) // 过滤掉读取失败的
            // 排序：文件夹在前，文件在后，按名称排序
            .sorted(Comparator.comparing((FileItemResponse item) -> item.type().equals("file"))
                    .thenComparing(FileItemResponse::name))
            .toList();
        }
    }

    // 创建文件或文件夹
    public void create(String parentPath, String name, boolean isFolder) throws IOException {
        Path targetPath = Paths.get(parentPath, name);
        if (Files.exists(targetPath)) {
            throw new RuntimeException("文件或文件夹已存在");
        }
        if (isFolder) {
            Files.createDirectories(targetPath);
        } else {
            Files.createFile(targetPath);
        }
    }

    // 读取文本内容
    public String readTextFile(String pathStr) throws IOException {
        Path path = Paths.get(pathStr);
        if (Files.size(path) > 10 * 1024 * 1024) { // 限制10MB
            throw new RuntimeException("文件过大，无法在线编辑");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    // 保存文本内容
    public void saveTextFile(String pathStr, String content) throws IOException {
        Path path = Paths.get(pathStr);
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // 删除文件或文件夹 (递归删除)
    public void delete(String pathStr) throws IOException {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) return;

        // 简单的递归删除逻辑
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // 下载文件 Resource 获取逻辑放在 Controller 中更合适，Service只负责检查
    public Path getFilePath(String pathStr) {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            throw new RuntimeException("文件不存在或不是文件");
        }
        return path;
    }

    // 辅助工具：格式化文件大小
    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private boolean isEditableFile(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String ext = filename.substring(lastDotIndex + 1).toLowerCase();
            return EDITABLE_EXTENSIONS.contains(ext);
        }
        return true; // 没有后缀的文件默认可编辑
    }
}
