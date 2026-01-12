package com.github.winefoxbot.core.config.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;

/**
 * 自定义自动切换代理选择器
 * 优先级：ThreadLocal强制指定 > 白名单(noProxyHosts) > 全局默认配置
 */
@Slf4j
public class AutoSwitchProxySelector extends ProxySelector {

    private final Proxy globalProxy;
    private final List<String> noProxyHosts;

    // 用于在代码中临时强制指定某个请求的代理（或强制直连）
    private static final ThreadLocal<Proxy> PROXY_THREAD_LOCAL = new ThreadLocal<>();

    public AutoSwitchProxySelector(ProxyConfig proxyConfig) {
        // 1. 初始化全局代理
        if (proxyConfig != null && proxyConfig.getEnabled()) {
            Proxy.Type type = proxyConfig.getType() == ProxyConfig.ProxyType.HTTP
                    ? Proxy.Type.HTTP
                    : Proxy.Type.SOCKS;
            this.globalProxy = new Proxy(type, new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()));
            this.noProxyHosts = proxyConfig.getNoProxyHosts();
            log.info("全局代理选择器已初始化: {}://{}:{}", type, proxyConfig.getHost(), proxyConfig.getPort());
        } else {
            this.globalProxy = Proxy.NO_PROXY;
            this.noProxyHosts = Collections.emptyList();
            log.info("全局代理选择器已初始化: 直连模式 (无代理)");
        }
    }

    @Override
    public List<Proxy> select(URI uri) {
        String host = uri.getHost();
        
        // 0. 基础校验
        if (host == null) {
            return Collections.singletonList(Proxy.NO_PROXY);
        }

        // ==========================================================
        // 策略1: 检查 ThreadLocal (最高优先级)
        // 适用场景: 某些特定代码块必须走特定代理，或者必须强制直连
        // ==========================================================
        Proxy manualProxy = PROXY_THREAD_LOCAL.get();
        if (manualProxy != null) {
            // 用完即焚，防止污染后续请求
            PROXY_THREAD_LOCAL.remove();
            if (log.isDebugEnabled()) {
                log.debug("[Proxy] ThreadLocal强制指定: {} -> {}", host, manualProxy);
            }
            return Collections.singletonList(manualProxy);
        }

        // ==========================================================
        // 策略2: 检查白名单 (noProxyHosts)
        // 适用场景: 内网地址、国内域名、bgm.tv 等需要直连的地址
        // ==========================================================
        if (!CollectionUtils.isEmpty(noProxyHosts)) {
            String lowerHost = host.toLowerCase();
            // 特殊日志：调试 bgm.tv
            boolean isDebugTarget = lowerHost.contains("bgm.tv");

            boolean shouldDirect = noProxyHosts.stream()
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .anyMatch(rule -> lowerHost.equals(rule) || lowerHost.endsWith("." + rule));

            if (shouldDirect) {
                if (isDebugTarget) {
                    log.info(">>> [Proxy] 域名 {} 命中白名单，强制直连", host);
                } else {
                    log.debug("[Proxy] 域名 {} 命中白名单，直连", host);
                }
                return Collections.singletonList(Proxy.NO_PROXY);
            }
        }

        // ==========================================================
        // 策略3: 使用全局默认代理
        // ==========================================================
        // 只有当全局代理不是 NO_PROXY 时才打印日志，避免日志刷屏
        if (globalProxy != Proxy.NO_PROXY) {
            log.debug("[Proxy] 域名 {} 使用全局代理: {}", host, globalProxy);
        }
        
        return Collections.singletonList(globalProxy);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        log.error("代理连接失败: URI={}, Proxy={}", uri, sa, ioe);
    }

    // ================== 静态工具方法 ==================

    /**
     * 临时强制当前线程的下一次请求使用直连
     */
    public static void forceDirect() {
        PROXY_THREAD_LOCAL.set(Proxy.NO_PROXY);
    }

    /**
     * 临时强制当前线程的下一次请求使用指定代理
     */
    public static void forceProxy(Proxy proxy) {
        PROXY_THREAD_LOCAL.set(proxy);
    }
    
    /**
     * 清除强制设置
     */
    public static void clearForce() {
        PROXY_THREAD_LOCAL.remove();
    }
}
