package com.github.winefoxbot.core.service.denpencyversion.impl;

import com.github.winefoxbot.core.service.denpencyversion.DependencyVersionService;
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

    /**
     * 获取 com.mikuac:shiro 的版本号示例
     * @return 版本号
     */
    public String getShiroBotVersion() {
        try {
            // 假设 'com.mikuac.shiro.Bot' 是该依赖中的一个真实存在的类
            // 你需要替换成实际的类名
            Class<?> shiroBotClass = Class.forName("com.mikuac.shiro.core.Bot"); // <-- 替换为实际的类名
            return getVersion(shiroBotClass);
        } catch (ClassNotFoundException e) {
            System.err.println("无法找到指定的类，请确认类名和依赖是否正确。");
            e.printStackTrace();
            return "dependency class not found";
        }
    }
}
