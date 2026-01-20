package com.github.winefoxbot.core.controller;


import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.model.vo.webui.req.filemanager.CreateFileRequest;
import com.github.winefoxbot.core.model.vo.webui.req.filemanager.SaveFileRequest;
import com.github.winefoxbot.core.model.vo.webui.resp.FileItemResponse;
import com.github.winefoxbot.core.service.webui.WebUIFileManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @author FlanChan
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class WebUIFileManagerController {

    private final WebUIFileManagerService webUIFileManagerService;

    /**
     * 获取文件列表
     * 返回 List<FileItem> -> 前端收到 Result<List<FileItem>>
     */
    @GetMapping("/list")
    public List<FileItemResponse> list(@RequestParam(required = false, defaultValue = "") String path) throws IOException {
        return webUIFileManagerService.listFiles(path);
    }

    /**
     * 新建文件/文件夹
     * 返回 String -> 前端收到 Result<String> (注意你的Handler里对String特殊处理了)
     */
    @PostMapping("/create")
    public Result<String> create(@RequestBody CreateFileRequest request) throws IOException {
        webUIFileManagerService.create(request.path(), request.name(), request.isFolder());
        return Result.ok("创建成功");
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete")
    public Result<String> delete(@RequestParam String path) throws IOException {
        webUIFileManagerService.delete(path);
        return Result.ok("删除成功");
    }

    /**
     * 读取文本内容
     */
    @GetMapping("/content")
    public Map<String, String> getContent(@RequestParam String path) throws IOException {
        String content = webUIFileManagerService.readTextFile(path);
        return Map.of("content", content);
    }

    /**
     * 保存文本内容
     */
    @PostMapping("/save")
    public Result<String> saveContent(@RequestBody SaveFileRequest request) throws IOException {
        webUIFileManagerService.saveTextFile(request.path(), request.content());
        return Result.ok("保存成功");
    }



    /**
     * 下载文件
     * 建议在 GlobalResponseHandler 中排除 ResponseEntity 类型
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String path) throws MalformedURLException {
        Path filePath = webUIFileManagerService.getFilePath(path);
        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +
                        URLEncoder.encode(filePath.getFileName().toString(), StandardCharsets.UTF_8) + "\"")
                .body(resource);
    }

    /**
     * 预览图片/文件流
     */
    @GetMapping("/view")
    public ResponseEntity<Resource> view(@RequestParam String path) throws IOException {
        Path filePath = webUIFileManagerService.getFilePath(path);
        Resource resource = new UrlResource(filePath.toUri());

        String mimeType = Files.probeContentType(filePath);
        if (mimeType == null) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(resource);
    }
}
