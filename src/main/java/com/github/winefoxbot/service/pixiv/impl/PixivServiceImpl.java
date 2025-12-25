package com.github.winefoxbot.service.pixiv.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.config.PixivConfig;
import com.github.winefoxbot.exception.PixivR18Exception;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.service.pixiv.PixivService;
import jakarta.annotation.PreDestroy; // 如果你使用 Spring Boot 2.x, 请改为 import javax.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.jsoup.Jsoup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.winefoxbot.utils.CommandUtil.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PixivServiceImpl implements PixivService {

    private static final String PIXIV_BASE = "https://www.pixiv.net";
    private final OkHttpClient httpClient;
    private final PixivConfig pixivConfig;
    private final ObjectMapper objectMapper;
    private final JobScheduler jobScheduler;

    // 创建一个在Service生命周期内共享的虚拟线程池，用于所有下载任务
    private final ExecutorService downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();

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
    public boolean isR18Artwork(String pid, Long groupId) throws IOException {
        String apiUrl = String.format("%s/ajax/illust/%s?lang=zh", PIXIV_BASE, pid);
        Request request = new Request.Builder()
                .url(apiUrl)
                .headers(pixivConfig.getHeaders())
                .removeHeader(HttpHeaders.COOKIE)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() &&
                    groupId != null &&
                    pixivConfig.getBanR18Groups() != null &&
                    pixivConfig.getBanR18Groups().contains(groupId)) {
                throw new PixivR18Exception();
            }
        }
        return false;
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
            String description = Jsoup.parse(nodeBody.path("description").asText()).text();
            String uid = tagsBody.path("authorId").asText();
            JsonNode tagsList = tagsBody.get("tags");
            List<String> tags = new ArrayList<>();
            tagsList.forEach(t -> tags.add(t.path("tag").asText()));
            return new PixivDetail(id, illustTitle, uid, userName, description, tags);
        }
    }

    @Override
    public void addSchedulePush(Long groupId) {
        jobScheduler.scheduleRecurrently(
                "PixivDailyPushJob-" + groupId,
                Cron.daily(12), // 每天中午12点执行
                () -> {}
        );
        jobScheduler.scheduleRecurrently(
                "PixivWeekPushJob-" + groupId,
                Cron.weekly(), // 每周执行
                () -> {}
        );
        jobScheduler.scheduleRecurrently(
                "PixivMonthPushJob-" + groupId,
                Cron.lastDayOfTheMonth(), // 月末执行
                () -> {}
        );

    }

    /**
     *
     * @param pid 作品ID
     * @return 一个代表未来文件列表的 CompletableFuture<List<File>>
     */
    @Override
    public CompletableFuture<List<File>> fetchImages(String pid) {
        // 使用 CompletableFuture.supplyAsync 将整个获取流程放入后台线程池执行
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 动图逻辑优先处理（此部分仍然在后台线程中同步执行）
                String ugoiraZipUrl = checkUgoiraZip(pid);
                if (ugoiraZipUrl != null) {
                    File gif = downloadUgoiraToGif(pid, ugoiraZipUrl);
                    if (gif.length() >= 15 * 1024 * 1024) {
                        compressImage(gif, 15);
                    }
                    return List.of(gif); // 动图处理完成，直接返回结果
                }

                // 2. 获取所有静态图的URL列表
                List<String> imageUrls = getStaticImageUrls(pid);
                if (imageUrls.isEmpty()) {
                    return List.of(); // 没有图片，返回空列表
                }
                log.info("PID: {}，发现 {} 张静态图片，提交并行的子任务...", pid, imageUrls.size());

                // 3. 为每个URL创建一个独立的、并行的下载处理子任务
                List<CompletableFuture<File>> individualImageFutures = imageUrls.stream()
                        .map(url -> CompletableFuture.supplyAsync(() -> downloadAndProcessSingleImage(url, pid), downloadExecutor))
                        .toList();

                // 4. 非阻塞地等待所有子任务完成，并收集结果
                // CompletableFuture.allOf() 创建一个在所有子任务完成后才完成的新Future
                // thenApply() 在新Future完成后，收集所有子任务的结果
                CompletableFuture<List<File>> allImagesFuture = CompletableFuture.allOf(individualImageFutures.toArray(new CompletableFuture[0]))
                        .thenApply(v -> individualImageFutures.stream()
                                .map(CompletableFuture::join) // 此处join是安全的，因为allOf保证了所有future已完成
                                .collect(Collectors.toList()));

                // 5. 在supplyAsync的lambda中，我们需要等待并返回最终结果
                return allImagesFuture.join();

            } catch (Exception e) {
                log.error("在异步 fetchImages 任务中发生严重错误, PID: {}", pid, e);
                // 将异常包装在CompletionException中，以便上游的 exceptionally() 可以捕获
                throw new CompletionException(e);
            }
        }, downloadExecutor);
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

    private File downloadAndProcessSingleImage(String url, String pid) {
        String name = url.substring(url.lastIndexOf("/") + 1);
        File targetFolder = pidFolderForImages(pid);
        File targetFile = new File(targetFolder, name);
        log.info("启动任务：下载 {} 到 {} [线程: {}]", name, targetFile.getPath(), Thread.currentThread().toString());
        Request request = new Request.Builder().url(url).headers(pixivConfig.getHeaders()).build();
        int retry = 0;
        while (retry < 10) {
            try (Response res = httpClient.newCall(request).execute()) {
                if (!res.isSuccessful()) {
                    throw new IOException("下载失败，HTTP Code: " + res.code());
                }
                ResponseBody body = res.body();
                if (body == null) {
                    throw new IOException("下载失败，响应体为空");
                }
                try (InputStream in = body.byteStream()) {
                    Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("下载完成 {}", name);
                if (targetFile.length() >= 10 * 1024 * 1024) {
                    log.info("文件 {} 大小超过10MB，开始压缩...", name);
                    compressImage(targetFile, 2);
                    log.info("文件 {} 压缩完成", name);
                }
                return targetFile;
            } catch (SSLHandshakeException e) {
                log.warn("SSL握手失败，正在重试... (第 {}/10 次)", retry + 1, e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("下载线程被中断", ie);
                }
                retry++;
            } catch (Exception e) {
                log.error("处理文件 {} 时发生严重错误", name, e);
                throw new RuntimeException("处理文件 " + name + " 失败", e);
            }
        }
        throw new RuntimeException("下载 " + name + " 失败，已达到最大重试次数");
    }

    private File pidFolderForImages(String pid) {
        File pidFolder = new File(pixivConfig.getImgRoot(), pid);
        if (!pidFolder.exists()) {
            pidFolder.mkdirs();
        }
        return pidFolder;
    }

    private String checkUgoiraZip(String pid) {
        String url = String.format("%s/ajax/illust/%s/ugoira_meta", PIXIV_BASE, pid);
        Request request = new Request.Builder().url(url).headers(pixivConfig.getHeaders()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            ResponseBody body = response.body();
            if (body == null) return null;
            JsonNode content = objectMapper.readTree(body.string());
            if (content.get("error").asBoolean()) return null;
            return content.get("body").get("originalSrc").asText();
        } catch (IOException e) {
            log.error("checkUgoiraZip 失败 pid={}", pid, e);
            return null;
        }
    }

    private File downloadUgoiraToGif(String pid, String zipUrl) throws Exception {
        File zipFile = new File(pixivConfig.getImgZipRoot(), pid + ".zip");
        Request zipReq = new Request.Builder().url(zipUrl).headers(pixivConfig.getHeaders()).build();
        try (Response res = httpClient.newCall(zipReq).execute()) {
            if (!res.isSuccessful()) throw new IOException("Zip download failed: " + res.code());
            ResponseBody body = res.body();
            if (body == null) throw new IOException("Zip download failed: empty body");
            try (InputStream in = body.byteStream()) {
                Files.copy(in, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        File extractDir = new File(pixivConfig.getImgZipRoot(), pid);
        if (!extractDir.exists()) extractDir.mkdirs();
        if (isWindows()) {
            runCmd("tar", "-xf", zipFile.getAbsolutePath(), "-C", extractDir.getAbsolutePath());
        } else {
            runCmd("unzip", "-o", zipFile.getAbsolutePath(), "-d", extractDir.getAbsolutePath());
        }
        File gifFile = new File(pixivConfig.getImgRoot(), pid + ".gif");
        runCmd("ffmpeg", "-r", "15", "-i", new File(extractDir, "%06d.jpg").getAbsolutePath(), gifFile.getAbsolutePath(), "-y");
        Files.deleteIfExists(zipFile.toPath());
        try (var files = Files.walk(extractDir.toPath())) {
            files.sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        }
        return gifFile;
    }

    private void compressImage(File file, long targetMB) throws Exception {
        File temp = new File(file.getParent(), "temp_" + System.nanoTime() + "_" + file.getName());
        File currentFile = file;
        while (currentFile.length() / 1024 / 1024 >= targetMB) {
            String info = runCmdGetOutput("ffmpeg", "-i", currentFile.getAbsolutePath());
            if (info == null) throw new IOException("ffmpeg -i command returned null for " + currentFile.getAbsolutePath());
            int[] wh = extractDimensions(info);
            int w = Math.max((int)(wh[0] * 0.8), 10);
            int h = -1;
            runCmd("ffmpeg", "-i", currentFile.getAbsolutePath(), "-vf", "scale=" + w + ":" + h, "-q:v", "4", temp.getAbsolutePath(), "-y");
            if (!temp.exists() || temp.length() == 0) {
                throw new IOException("ffmpeg failed to create or wrote an empty temp file.");
            }
            if (currentFile != file) {
                Files.deleteIfExists(currentFile.toPath());
            }
            currentFile = temp;
            temp = new File(file.getParent(), "temp_" + System.nanoTime() + "_" + file.getName());
        }
        if (currentFile != file) {
            Files.move(currentFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
}
