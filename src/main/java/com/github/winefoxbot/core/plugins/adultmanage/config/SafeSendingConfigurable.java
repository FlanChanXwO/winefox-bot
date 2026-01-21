package com.github.winefoxbot.core.plugins.adultmanage.config;

/**
 * 安全发送配置协定接口
 * 让具体的 PluginConfig 实现此接口，以便通用服务能读取通用的配置项
 */
public interface SafeSendingConfigurable {

    /**
     * 是否开启自动撤回
     */
    boolean isRevokeEnabled();

    /**
     * 撤回延迟（秒）
     */
    int getRevokeDelay();
}
