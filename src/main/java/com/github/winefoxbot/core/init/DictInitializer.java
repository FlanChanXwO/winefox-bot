package com.github.winefoxbot.core.init;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.github.winefoxbot.core.service.common.SmartTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * 词库初始化器
 * 自动扫描 classpath:data/dict/ 下所有的 .txt 文件并加载
 * @author FlanChan
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DictInitializer implements ApplicationRunner {

    private final SmartTagService smartTagService;

    @Override
    public void run(ApplicationArguments args) {
        log.info(">>> 开始初始化搜索词库...");
        long start = System.currentTimeMillis();

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        
        try {
            // 扫描 JAR 包内和文件系统中的所有匹配文件
            Resource[] resources = resolver.getResources("classpath*:data/dict/*.txt");

            if (resources.length == 0) {
                log.warn("!!! 未找到任何词典文件，请检查 data/dict/ 路径");
                return;
            }

            for (Resource resource : resources) {
                loadSingleDict(resource);
            }

        } catch (Exception e) {
            log.error("词库加载失败", e);
        }

        long end = System.currentTimeMillis();
        log.info(">>> 词库初始化完成，耗时: {}ms，当前生效保护词数: {}", 
                 (end - start), smartTagService.getDictSize());
    }

    private void loadSingleDict(Resource resource) {
        try {
            // 使用 Hutool 的 IoUtil 快速读取流中的每一行
            // JDK 21 try-with-resources
            try (var reader = IoUtil.getReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                
                int fileCount = 0;
                // Hutool IoUtil.readLines 更加方便
                for (String line : IoUtil.readLines(reader, new ArrayList<>())) {
                    if (StrUtil.isBlank(line) || line.trim().startsWith("#")) {
                        continue;
                    }

                    // 兼容格式: "碧蓝档案 nz 100" 或 "碧蓝档案"
                    // split 默认使用正则，\\s+ 匹配任意空白
                    String[] parts = line.split("\\s+");
                    String word = parts[0];

                    // 只有大于1个字的才算词
                    if (word.length() > 1) {
                        smartTagService.addProtectedWord(word);
                        fileCount++;
                    }
                }
                log.info("   └─ 加载词典 [{}]: {} 个词", resource.getFilename(), fileCount);
            }
        } catch (Exception e) {
            log.error("   └─ 加载词典 [{}] 失败: {}", resource.getFilename(), e.getMessage());
        }
    }
}
