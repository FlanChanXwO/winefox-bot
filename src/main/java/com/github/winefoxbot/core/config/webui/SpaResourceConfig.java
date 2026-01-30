package com.github.winefoxbot.core.config.webui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    // 读取配置文件中的静态资源位置
    // 默认为 classpath:/static/ (开发环境通常用这个)
    @Value("${spring.web.resources.static-locations:classpath:/static/}")
    private String[] staticLocations;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                // 1. 动态注入路径：开发环境注入 classpath，生产环境注入 file
                .addResourceLocations(staticLocations) 
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // A. 正常资源查找 (比如 /logo.png, /style.css, /file.html)
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // B. 目录访问查找 (比如 /logs/live -> /logs/live/index.html)
                        // 这是解决 trailingSlash: true 产生的文件夹结构的关键
                        Resource indexResource = location.createRelative(resourcePath + "/index.html");
                        if (indexResource.exists() && indexResource.isReadable()) {
                            return indexResource;
                        }
                        
                        // C. 补全斜杠查找 (比如 /logs/live (不带斜杠) -> /logs/live/index.html)
                        // 防止用户输入 URL 时没加斜杠
                        if (!resourcePath.endsWith("/")) {
                             Resource indexResourceSlash = location.createRelative(resourcePath + "/index.html");
                             if (indexResourceSlash.exists() && indexResourceSlash.isReadable()) {
                                return indexResourceSlash;
                             }
                        }

                        return null;
                    }
                });
    }
}
