package com.github.winefoxbot;


import com.mikuac.shiro.boot.ShiroAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-06-21:10
 */
@SpringBootTest()
public class MavenVersionReader {



    public  String getVersion(String groupId, String artifactId) {
        String path = "/META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties";
        Properties props = new Properties();
        try (InputStream is = WineFoxBotApp.class.getResourceAsStream(path)) {
            if (is != null) {
                props.load(is);
                return props.getProperty("version");
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 文件不存在或读取错误
        }
        return "unknown";
    }

    @Test
    public void test () {
        // 示例：获取 shiro 的版本
        String shiroVersion = getVersion("com.mikuac", "shiro");
        System.out.println("Shiro version: " + shiroVersion);

        // 示例：获取 Spring Core 的版本
        String springVersion = getVersion("org.springframework", "spring-core");
        System.out.println("Spring Core version: " + springVersion);
    }
}