package com.github.winefoxbot.plugins.illegalink.service.impl;

import com.github.winefoxbot.plugins.illegalink.service.DomainAllowListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@Lazy
public class DomainAllowListServiceImpl implements DomainAllowListService {

    // 从 classpath 读取我们的白名单文件
    @Value("classpath:allow-domain-list.txt")
    private Resource allowListResource;

    private final List<String> allowedPatterns = new CopyOnWriteArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AtomicBoolean initialized = new AtomicBoolean(false);


    /**
     * 确保白名单已经从文件中加载。
     * 使用双重检查锁定（DCL）模式来保证只加载一次。
     */
    private void ensureLoaded() {
        // 第一次检查，非阻塞，性能高
        if (!initialized.get()) {
            // 使用 synchronized 块保证只有一个线程可以执行加载操作
            synchronized (this) {
                // 第二次检查，防止其他线程已在此期间完成加载
                if (!initialized.get()) {
                    loadAllowList();
                    initialized.set(true); // 设置标志位，表示已加载
                }
            }
        }
    }

    /**
     * 实际加载白名单文件的方法。
     * 注意：此方法现在是私有的，并且不再有 @PostConstruct 注解。
     */
    private void loadAllowList() {
        if (!allowListResource.exists()) {
            log.warn("域名白名单文件 'allow-domain-list.txt' 不存在，将允许所有域名。");
            return;
        }

        log.info("首次需要，开始加载域名白名单...");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(allowListResource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> patterns = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());

            allowedPatterns.addAll(patterns);
            log.info("成功懒加载 {} 条域名白名单规则。", allowedPatterns.size());

        } catch (IOException e) {
            log.error("懒加载域名白名单文件失败！", e);
            // 抛出异常或采取其他错误处理措施
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
        ensureLoaded();

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
