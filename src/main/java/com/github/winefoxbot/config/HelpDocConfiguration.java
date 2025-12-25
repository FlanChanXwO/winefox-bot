package com.github.winefoxbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.model.dto.helpdoc.HelpDoc;
import com.github.winefoxbot.model.dto.helpdoc.HelpSystemConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class HelpDocConfiguration {
    private HelpSystemConfig systemConfig;
    private final ObjectMapper mapper;

    @PostConstruct
    public void init() {
        try {
            // 假设文件在 resources 目录下
            InputStream inputStream = getClass().getResourceAsStream("/help-docs.json");
            this.systemConfig = mapper.readValue(inputStream, HelpSystemConfig.class);
        } catch (IOException e) {
            // 错误处理，例如记录日志或抛出异常
            e.printStackTrace();
            // 初始化一个空对象以避免 NullPointerException
            this.systemConfig = new HelpSystemConfig();
        }
    }

    // 提供获取排序列表的方法
    public List<String> getGroupOrder() {
        return (systemConfig != null && systemConfig.getGroupOrder() != null)
                ? systemConfig.getGroupOrder()
                : List.of();
    }

    // 提供获取文档列表的方法
    public List<HelpDoc> getDocumentation() {
        return (systemConfig != null && systemConfig.getDocumentation() != null)
                ? systemConfig.getDocumentation()
                : List.of();
    }
}
