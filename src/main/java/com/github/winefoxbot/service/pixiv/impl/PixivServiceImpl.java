package com.github.winefoxbot.service.pixiv.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.PixivConfig;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.enums.PixivArtworkType;
import com.github.winefoxbot.service.file.FileStorageService;
import com.github.winefoxbot.service.pixiv.PixivService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.winefoxbot.utils.CommandUtil.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PixivServiceImpl implements PixivService {


    private final OkHttpClient httpClient;
    private final PixivConfig pixivConfig;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final ExecutorService downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, Lock> pidLocks = new ConcurrentHashMap<>();

    // 常量
    private static final String PIXIV_BASE = "https://www.pixiv.net";
    private static final Duration CACHE_EXPIRATION = Duration.ofHours(6); // 缓存过期时间
    private static final String PIXIV_IMAGE_SUBFOLDER = "pixiv/images";

    private static final boolean ENABLE_COMPRESSION = false;

    private final Semaphore downloadPermits = new Semaphore(20);

    /**
     * 异步获取作品图片，优先使用本地缓存。
     *
     * @param pid 作品ID
     * @return 一个代表未来文件列表的 CompletableFuture<List<File>>
     */
    @Override
    public CompletableFuture<List<File>> fetchImages(String pid) {
        // 使用 computeIfAbsent 来原子性地获取或创建锁，确保每个PID只有一个锁实例
        Lock pidLock = pidLocks.computeIfAbsent(pid, k -> new ReentrantLock());
        return CompletableFuture.supplyAsync(() -> {
            String pidRelativePath = PIXIV_IMAGE_SUBFOLDER + "/" + pid;
            boolean lockAcquired = false;
            try {
                // 等待获取特定PID的锁
                if (pidLock.tryLock(2, TimeUnit.MINUTES)) {
                    lockAcquired = true;
                    log.info("Acquired lock for PID: {}", pid);
                    try {
                        List<Path> cachedPaths = fileStorageService.listFiles(pidRelativePath);
                        if (cachedPaths != null && !cachedPaths.isEmpty()) {
                            List<File> cachedFiles = cachedPaths.stream()
                                    .map(Path::toFile)
                                    .filter(file -> file.length() > 0)
                                    .collect(Collectors.toList());
                            if (!cachedFiles.isEmpty()) {
                                log.info("PID: {} 命中缓存，找到 {} 个文件，直接返回。", pid, cachedFiles.size());
                                cachedFiles.forEach(file -> fileStorageService.registerFile(file.toPath(), CACHE_EXPIRATION, null));
                                return cachedFiles;
                            }
                        }

                    } catch (IOException e) {
                        log.info("检查 PID: {} 的缓存时未找到文件或出错，将继续下载。({})", pid, e.getClass().getSimpleName());
                    }

                    log.info("PID: {} 缓存未命中或为空，开始从网络获取。", pid);
                    try {
                        String ugoiraZipUrl = checkUgoiraZip(pid);
                        if (ugoiraZipUrl != null) {
                            log.info("PID: {} 识别为动图，开始下载并转换为GIF...", pid);
                            File gif = downloadUgoiraToGif(pid, ugoiraZipUrl);
                            if (ENABLE_COMPRESSION && gif.length() >= 15 * 1024 * 1024) {
                                gif = compressImage(gif, 15, pid);
                            }
                            return List.of(gif);
                        }

                        List<String> imageUrls = getStaticImageUrls(pid);
                        if (imageUrls.isEmpty()) return List.of();

                        log.info("PID: {}，发现 {} 张静态图片，提交并行下载任务...", pid, imageUrls.size());
                        List<CompletableFuture<File>> imageFutures = imageUrls.stream()
                                .map(url -> CompletableFuture.supplyAsync(() -> downloadAndProcessSingleImage(url, pid), downloadExecutor))
                                .toList();

                        List<File> downloadedFiles = CompletableFuture.allOf(imageFutures.toArray(new CompletableFuture[0]))
                                .thenApply(v -> imageFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                                .join();

                        return downloadedFiles; // 简化返回，压缩逻辑可以在 downloadAndProcessSingleImage 内部处理或像之前一样并行处理

                    } catch (Exception e) {
                        log.error("异步 fetchImages 任务中发生严重错误, PID: {}", pid, e);
                        throw new CompletionException(e);
                    }
                } else {
                    log.warn("Could not acquire lock for PID: {} within timeout.", pid);
                    // 考虑返回一个特定的异常或空列表，让调用者知道操作超时
                    return List.of();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 恢复中断状态
                log.error("Lock acquisition interrupted for PID: {}", pid, e);
                throw new CompletionException(e);
            } finally {
                if (lockAcquired) {
                    pidLock.unlock();
                    log.info("Released lock for PID: {}", pid);
                    // 可选：当确定一个PID在短时间内不会被再次请求时，可以考虑移除锁以节省内存
                    // 但对于机器人这类应用，为简单起见，一直保留也可以接受
                    // pidLocks.remove(pid);
                }
            }
        }, downloadExecutor);
    }


    private File downloadAndProcessSingleImage(String url, String pid) {
        try {
            // 在开始下载前获取一个许可，如果许可不够，当前线程会阻塞在这里
            downloadPermits.acquire();
            String name = url.substring(url.lastIndexOf("/") + 1);
            String relativePath = PIXIV_IMAGE_SUBFOLDER + "/" + pid + "/" + name;
            log.info("启动下载任务：{} ", relativePath);
            Request request = new Request.Builder().url(url).headers(pixivConfig.getHeaders()).build();
            int retry = 0;
            while (retry < 10) {
                try (Response res = httpClient.newCall(request).execute()) {
                    if (!res.isSuccessful()) throw new IOException("下载失败，HTTP Code: " + res.code());
                    ResponseBody body = res.body();
                    if (body == null) throw new IOException("下载失败，响应体为空");
                    try (InputStream in = body.byteStream()) {
                        Path finalPath = fileStorageService.writeFile(relativePath, in, CACHE_EXPIRATION, null);
                        log.info("下载完成并注册缓存：{}", finalPath.toAbsolutePath());
                        return finalPath.toFile();
                    }
                } catch (SSLHandshakeException e) {
                    log.warn("SSL握手失败，正在重试... (第 {}/10 次) for {}", retry + 1, name);
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                    retry++;
                } catch (Exception e) {
                    log.error("处理文件 {} 时发生严重错误", name, e);
                    throw new RuntimeException("处理文件 " + name + " 失败", e);
                }
            }
            throw new RuntimeException("Download failed for " + name + " after max retries.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Download task for {} was interrupted while waiting for permit.", url);
            throw new RuntimeException("Download interrupted", e);
        } finally {
            // 无论下载成功、失败还是抛出异常，都必须释放许可！
            downloadPermits.release();
            log.info("Permit released for {}", url);
        }
    }

    private File downloadUgoiraToGif(String pid, String zipUrl) throws Exception {
        Path tempDir = Files.createTempDirectory("ugoira-" + pid + "-");
        try {
            Path zipFile = tempDir.resolve(pid + ".zip");
            Path extractDir = tempDir.resolve("extracted");
            Path tempGif = tempDir.resolve(pid + ".gif");

            Request zipReq = new Request.Builder().url(zipUrl).headers(pixivConfig.getHeaders()).build();
            try (Response res = httpClient.newCall(zipReq).execute(); InputStream in = res.body().byteStream()) {
                if (!res.isSuccessful()) throw new IOException("Zip download failed: " + res.code());
                Files.copy(in, zipFile); // 写入临时文件，这是允许的
            }

            Files.createDirectories(extractDir);
            if (isWindows()) {
                runCmd("tar", "-xf", zipFile.toAbsolutePath().toString(), "-C", extractDir.toAbsolutePath().toString());
            } else {
                runCmd("unzip", "-o", zipFile.toAbsolutePath().toString(), "-d", extractDir.toAbsolutePath().toString());
            }

            runCmd("ffmpeg", "-r", "15", "-i", extractDir.resolve("%06d.jpg").toAbsolutePath().toString(), tempGif.toAbsolutePath().toString(), "-y");

            String relativeGifPath = PIXIV_IMAGE_SUBFOLDER + "/" + pid + "/" + pid + ".gif";
            try (InputStream gifStream = Files.newInputStream(tempGif)) {
                return fileStorageService.writeFile(relativeGifPath, gifStream, CACHE_EXPIRATION, null).toFile();
            }
        } finally {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", path);
                    }
                });
            }
        }
    }


    private List<String> getStaticImageUrls(String pid) throws IOException {
        String apiUrl = String.format("%s/ajax/illust/%s/pages?lang=zh", PIXIV_BASE, pid);
        Request req = new Request.Builder().url(apiUrl).headers(pixivConfig.getHeaders()).build();

        try (Response httpResponse = httpClient.newCall(req).execute()) {
            if (!httpResponse.isSuccessful()) {
                throw new IOException("获取图片URL列表失败: " + httpResponse.code());
            }
            ResponseBody body = httpResponse.body();
            if (body == null) {
                throw new IOException("响应体为空 for PID: " + pid);
            }
            JsonNode root = objectMapper.readTree(body.string());
            if (root.get("error").asBoolean()) {
                log.warn("API返回错误 for PID {}: {}", pid, root.get("message").asText());
                return List.of();
            }
            List<String> urlList = new ArrayList<>();
            JsonNode bodyArray = root.get("body");
            if (bodyArray != null && bodyArray.isArray()) {
                for (JsonNode item : bodyArray) {
                    JsonNode urls = item.get("urls");
                    if (urls != null) {
                        JsonNode original = urls.get("original");
                        if (original != null) {
                            urlList.add(original.asText());
                        }
                    }
                }
            }
            return urlList;
        }
    }

    private String checkUgoiraZip(String pid) {
        String url = String.format("%s/ajax/illust/%s/ugoira_meta", PIXIV_BASE, pid);
        Request request = new Request.Builder().url(url).headers(pixivConfig.getHeaders()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            JsonNode content = objectMapper.readTree(body.string());
            if (content.get("error").asBoolean()) {
                return null;
            }
            return content.get("body").get("originalSrc").asText();
        } catch (IOException e) {
            log.error("checkUgoiraZip 失败 pid={}", pid, e);
            return null;
        }
    }


    private File compressImage(File originalFile, long targetMB, String pid) throws Exception {
        Path tempDir = Files.createTempDirectory("compress-" + originalFile.getName() + "-");
        try {
            Path currentPath = originalFile.toPath();
            String originalName = originalFile.getName();

            while (Files.size(currentPath) / 1024 / 1024 >= targetMB) {
                Path tempOut = tempDir.resolve("temp_" + System.nanoTime() + "_" + originalName);
                String info = runCmdGetOutput("ffmpeg", "-i", currentPath.toAbsolutePath().toString());
                if (info == null) throw new IOException("ffmpeg -i command returned null for " + currentPath);

                int[] wh = extractDimensions(info);
                int w = Math.max((int) (wh[0] * 0.8), 10);

                runCmd("ffmpeg", "-i", currentPath.toAbsolutePath().toString(), "-vf", "scale=" + w + ":-1", "-q:v", "4", tempOut.toAbsolutePath().toString(), "-y");

                if (Files.notExists(tempOut) || Files.size(tempOut) == 0) {
                    throw new IOException("ffmpeg failed to create or wrote an empty temp file.");
                }

                // 如果当前文件不是原始文件（即上一次循环的临时输出），则删除它
                if (currentPath != originalFile.toPath()) {
                    Files.delete(currentPath);
                }
                currentPath = tempOut;
            }

            // 如果发生了压缩（currentPath不再是原始文件），则将最终的压缩结果通过FileStorageService写回
            if (currentPath != originalFile.toPath()) {
                String relativePath = PIXIV_IMAGE_SUBFOLDER + "/" + pid + "/" + originalName;
                try (InputStream finalStream = Files.newInputStream(currentPath)) {
                    log.info("压缩完成，正在通过FileStorageService保存新文件: {}", relativePath);
                    return fileStorageService.writeFile(relativePath, finalStream, CACHE_EXPIRATION, null).toFile();
                }
            } else {
                // 如果文件大小本来就达标，没有发生压缩，则返回原文件
                return originalFile;
            }
        } finally {
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp compression file: {}", path);
                    }
                });
            }
        }
    }

    private int[] extractDimensions(String ffmpegOutput) {
        Pattern pattern = Pattern.compile("\\s(\\d{2,})x(\\d{2,})\\s");
        Matcher matcher = pattern.matcher(ffmpegOutput);
        if (matcher.find()) {
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        } else {
            throw new IllegalArgumentException("无法从ffmpeg输出中提取尺寸: " + ffmpegOutput);
        }
    }

    // 使用@PreDestroy注解确保应用关闭时线程池被优雅地关闭
    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down Pixiv download executor...");
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Download executor did not terminate in 5 seconds. Forcing shutdown.");
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Error while waiting for executor to terminate", e);
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Pixiv download executor has been shut down.");
    }

    // --- 元数据获取和验证方法 (保持不变) ---

    @Override
    public boolean isPixivURL(String msg) {
        Pattern pattern = Pattern.compile(PIXIV_BASE + "/artworks/(\\d+)|illust_id=(\\d+)");
        return pattern.matcher(msg).find();
    }

    @Override
    public String extractPID(String msg) {
        if (msg == null) return null;
        if (msg.matches("\\d+")) return msg;

        Pattern pattern = Pattern.compile(PIXIV_BASE + "/artworks/(\\d+)|illust_id=(\\d+)");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) return matcher.group(i);
            }
        }
        return null;
    }

    @Override
    public boolean isValidPixivPID(String pid) throws IOException {
        if (pid == null || !pid.matches("\\d+") || pid.length() < 5 || pid.length() > 20) {
            return false;
        }
        String url = PIXIV_BASE + "/artworks/" + pid;
        Request request = new Request.Builder().url(url).headers(pixivConfig.getHeaders()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.code() != HttpStatus.NOT_FOUND.value();
        }
    }

    @Override
    public PixivDetail getPixivArtworkDetail(String pid) throws IOException {
        String apiUrl = String.format("%s/ajax/illust/%s?lang=zh", PIXIV_BASE, pid);
        Request request = new Request.Builder()
                .url(apiUrl)
                .headers(pixivConfig.getHeaders())
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null for PID: " + pid);
            }
            JsonNode jsonNode = objectMapper.readTree(body.string());
            JsonNode nodeBody = jsonNode.get("body");
            String id = nodeBody.path("illustId").asText();
            String illustTitle = nodeBody.path("illustTitle").asText();
            String userName = nodeBody.path("userName").asText();
            JsonNode tagsBody = nodeBody.get("tags");
            Boolean isR18 = nodeBody.get("xRestrict").asInt(0) == 1;
            PixivArtworkType artworkType = PixivArtworkType.fromValue(nodeBody.path("illustType").asInt(0));
            String description = Jsoup.parse(nodeBody.path("description").asText()).text();
            String uid = tagsBody.path("authorId").asText();
            JsonNode tagsList = tagsBody.get("tags");
            List<String> tags = new ArrayList<>();
            tagsList.forEach(t -> tags.add(t.path("tag").asText()));
            return new PixivDetail(id, illustTitle, uid, userName, description, isR18, artworkType, tags);
        }
    }

}
