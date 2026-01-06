package com.github.winefoxbot.core.service.denpencyversion.impl;

import com.github.winefoxbot.core.service.denpencyversion.DependencyVersionService;
import com.mikuac.shiro.core.Bot;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DependencyVersionServiceImpl implements DependencyVersionService {

    /**
     * 获取指定类的所在依赖包的版本号
     *
     * @param aClass 依赖包中的任意一个类的 Class 对象
     * @return 版本号字符串，如果获取失败则返回 null 或 "unknown"
     */
    @Override
    public Optional<String> getVersion(Class<?> aClass) {
        if (aClass == null) {
            return Optional.empty();
        }
        // Package 对象包含了版本信息
        Package aPackage = aClass.getPackage();
        if (aPackage != null) {
            // 首先尝试 Implementation-Version，这是标准属性
            String version = aPackage.getImplementationVersion();
            if (version != null) {
                return Optional.of(version);
            }
            // 如果上面是 null, 尝试 Specification-Version
            version = aPackage.getSpecificationVersion();
            if (version != null) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getShiroBotVersion() {
        Optional<String> shiroBotVersionOpt = this.getVersion(Bot.class);
        return shiroBotVersionOpt.orElse("2.5.0");
    }
}
