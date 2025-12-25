package com.github.winefoxbot.service.core.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.service.core.UpdateService;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-20:08
 */
@Service
@RequiredArgsConstructor
public class UpdateServiceImpl implements UpdateService {
    private static final String API_URL = "https://api.github.com/repos/FlanChanXwO/shiro-analysis-bilibili-plugin/releases/latest";

    private final OkHttpClient httpClient;
    private final  ObjectMapper mapper = new ObjectMapper();



    /**
     * 下载新 jar 并覆盖旧 jar
     * @param downloadUrl github release 下载 URL
     * @param currentJarPath 当前 jar 路径（即要覆盖的文件）
     */
    @Override
    public void updateJar(String downloadUrl, String currentJarPath) throws Exception {
        File tempFile = downloadJar(downloadUrl, currentJarPath);
        // 下载完成后覆盖原 jar
        File currentJar = new File(currentJarPath);
        if (currentJar.exists()) {
            if (!currentJar.delete()) {
                throw new RuntimeException("无法删除旧 jar 文件");
            }
        }
        if (!tempFile.renameTo(currentJar)) {
            throw new RuntimeException("无法覆盖旧 jar 文件");
        }
        // 启动新版本
        restartWithNewJar(currentJarPath);
    }




    private File downloadJar(String downloadUrl, String currentJarPath) throws IOException {
        File tempFile = new File(currentJarPath + ".tmp");
        // 下载到临时文件
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Java updater)");
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }


    @Override
    public ReleaseInfo getLatestRelease() throws Exception {
        Request request = new Request.Builder()
                .url(API_URL)
                .get()
                .build();
        try(Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JsonNode root = mapper.readTree(response.body().string());
                String tagName = root.get("tag_name").asText();
                String downloadUrl = root.get("assets").get(0).get("browser_download_url").asText();
                return new ReleaseInfo(tagName, downloadUrl);
            }
        }
        return null;
    }

    public static class ReleaseInfo {
        public final String version;
        public final String url;

        public ReleaseInfo(String version, String url) {
            this.version = version;
            this.url = url;
        }
    }


    private void restartWithNewJar(String newJarPath) throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        // 启动新 jar
        ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", newJarPath);
        builder.start();
        // 退出当前程序
        System.exit(0);
    }
}