package com.github.winefoxbot;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import com.github.winefoxbot.config.http.ProxyConfig;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-14:46
 */
public class PixivAuthorCrawlTest {
    public static void main(String[] args) {
        String baseUrl = "https://www.pixiv.net/ajax/user/66353827/profile/all?sensitiveFilterMode=userSetting&lang=zh";
        Dict dict = YamlUtil.loadByPath("D:\\SourceCode\\GithubProjects\\winefox-bot\\winefox-bot-shiro\\src\\main\\resources\\application.yaml");
        String cookie = dict.getByPath("pixiv.cookie", String.class);
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 7889));
        OkHttpClient client = new OkHttpClient.Builder().proxy(proxy).build();
        Headers headers = Headers.of(
                HttpHeaders.REFERER, "https://www.pixiv.net",
                HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 Chrome/80.0.3987.163 Safari/537.36",
                HttpHeaders.COOKIE, cookie
        );
        Request request = new Request.Builder().url(baseUrl)
                .headers(headers)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful())
                System.out.println(response.body().string());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}