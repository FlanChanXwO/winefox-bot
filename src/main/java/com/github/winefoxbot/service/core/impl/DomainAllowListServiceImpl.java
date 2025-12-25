package com.github.winefoxbot.service.core.impl;

import com.github.winefoxbot.service.core.DomainAllowListService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DomainAllowListServiceImpl implements DomainAllowListService {

    // 从 classpath 读取我们的白名单文件
    @Value("classpath:allow-domain-list.txt")
    private Resource allowListResource;

    // 使用线程安全的列表，适用于读多写少的场景
    private final List<String> allowedPatterns = new CopyOnWriteArrayList<>();

    // Spring 内置的通配符匹配器，完美支持 '*'
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 在 Bean 初始化后执行，用于加载白名单文件。
     */
    @PostConstruct
    public void loadAllowList() {
        if (!allowListResource.exists()) {
            log.warn("域名白名单文件 'allow-domain-list.txt' 不存在，将允许所有域名。");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(allowListResource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> patterns = reader.lines()
                    .map(String::trim) // 去除首尾空格
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")) // 忽略空行和注释行
                    .collect(Collectors.toList());
            
            allowedPatterns.addAll(patterns);
            log.info("成功加载 {} 条域名白名单规则。", allowedPatterns.size());

        } catch (IOException e) {
            log.error("加载域名白名单文件失败！", e);
            // 可以在这里决定是否启动失败
        }
    }

    /**
     * 检查给定的域名是否在白名单内。
     *
     * @param domain 要检查的域名, e.g., "sub.example.com"
     * @return 如果允许则返回 true, 否则返回 false
     */
    @Override
    public boolean isDomainAllowed(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }

        // 将域名转为小写以进行不区分大小写的匹配
        String lowerCaseDomain = domain.toLowerCase();

        for (String pattern : allowedPatterns) {
            if (pathMatcher.match(pattern.toLowerCase(), lowerCaseDomain)) {
                log.debug("✔️ 域名 '{}' 匹配规则 '{}'，允许访问。", domain, pattern);
                return true;
            }
        }

        log.warn("❌ 域名 '{}' 未匹配任何白名单规则，将被拒绝。", domain);
        return false;
    }
}
