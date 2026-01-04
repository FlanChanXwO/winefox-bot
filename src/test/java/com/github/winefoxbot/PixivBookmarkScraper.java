package com.github.winefoxbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.Data;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

public class PixivBookmarkScraper {

    public static void main(String[] args) {
        // ... (前面的 OkHttp 设置代码保持不变) ...
        String url = "https://www.pixiv.net/ajax/user/25649510/illusts/bookmarks?tag=&offset=0&limit=48&rest=show&lang=zh";
        String cookie = "PHPSESSID=25649510_hrDTVtO8QcSJYRlFVtwi0AVFS8bQsF1s; p_ab_id=5;";
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 7889));

        OkHttpClient client = new OkHttpClient.Builder().proxy(proxy).build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Cookie", cookie)
                .addHeader(HttpHeaders.ACCEPT, "application/json")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://www.pixiv.net/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body != null) {
                    String jsonStr = body.string();

                    // 1. 使用 JsonPath 提取出 "works" 数组部分
                    // JsonPath.read 返回的是一个实现了 List 接口的对象，但内部是其自己的 JSONArray 类型
                    Object worksNode = JsonPath.read(jsonStr, "$.body.works");

                    // 2. 创建 Jackson 的 ObjectMapper 实例
                    ObjectMapper objectMapper = new ObjectMapper();
                    // 如果 JSON 中有未知属性，默认会报错，可以配置忽略
                     objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                    // 3. 使用 ObjectMapper 将提取出的数据转换为我们想要的 List<ArtWork>
                    // 需要使用 TypeReference 来处理泛型列表
                    List<ArtWork> artWorks = objectMapper.convertValue(worksNode, new TypeReference<>() {
                    });

                    // 4. 现在 artWorks 就是一个真正的 ArtWork 对象列表了
                    System.out.println("成功解析出 " + artWorks.size() + " 个作品。");
                    if (!artWorks.isEmpty()) {
                        System.out.println("----------- 第一个作品对象 -----------");
                        System.out.println(artWorks.get(0));
                        System.out.println("ID: " + artWorks.get(0).getId());
                        System.out.println("URL: " + artWorks.get(0).getUrl());
                        System.out.println("------------------------------------");
                    }
                }
            } else {
                System.err.println("请求失败! 响应码: " + response.code());
                if (response.body() != null) {
                    System.err.println("错误响应体: " + response.body().string());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Data
    static class ArtWork {
        private String id;
        private String title;
        private Integer illustType;
        private String url;

        // 告诉 Jackson: JSON 数据中的 "xRestrict" 字段应该映射到这个 "xRestrict" 属性上
        @JsonProperty("xRestrict")
        private Integer xRestrict;
    }

}
