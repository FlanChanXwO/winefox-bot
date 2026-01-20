package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.init.ConfigMetadataRegistrar;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.model.vo.webui.req.config.UpdateConfigRequest;
import com.github.winefoxbot.core.model.vo.webui.resp.PluginConfigSchemaResponse;
import com.github.winefoxbot.core.model.vo.webui.resp.PluginListItemResponse;
import com.github.winefoxbot.core.service.plugin.PluginConfigService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 插件管理控制器
 * 处理插件列表显示、筛选、开关及配置动态设置
 * @author FlanChan
 */
@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class WebUIPluginController {

    private final PluginConfigService pluginConfigService;
    private final ConfigManager configManager;
    private final ConfigMetadataRegistrar configRegistrar;

    /**
     * 1. 获取插件列表
     * @param keyword 搜索关键词 (可选)
     */
    @GetMapping("/list")
    @Operation(summary = "获取插件列表")
    public Result<List<PluginListItemResponse>> getPlugins(@RequestParam(required = false) String keyword) {
        return Result.ok(pluginConfigService.getPluginList(keyword));
    }

    /**
     * 2. 切换插件开启/关闭
     */
    @PostMapping("/{pluginId}/toggle")
    @Operation(summary = "切换插件状态")
    public Result<Void> togglePlugin(
            @PathVariable String pluginId,
            @RequestParam boolean enable) {
        
        pluginConfigService.togglePlugin(pluginId, enable);
        return Result.ok();
    }

    /**
     * 3. 获取插件的配置定义 (Schema) 和当前值
     * 前端点击 "配置" 按钮时调用此接口，动态生成表单
     */
    @GetMapping("/{pluginId}/config-schema")
    @Operation(summary = "获取插件配置定义")
    public Result<PluginConfigSchemaResponse> getPluginConfig(@PathVariable String pluginId) {
        try {
            return Result.ok(pluginConfigService.getPluginConfigSchema(pluginId));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 4. 保存插件配置 (Global Scope)
     * 复用通用的 Config 逻辑，或者针对插件做特化
     */
    @PostMapping("/config/save")
    @Operation(summary = "保存插件配置")
    public Result<Void> savePluginConfig(@RequestBody UpdateConfigRequest req) {

        ConfigManager.Scope requestedScope = req.scope() != null
                ? ConfigManager.Scope.valueOf(req.scope().toUpperCase())
                : ConfigManager.Scope.GLOBAL;

        // ★ 核心：使用 registrar 获取配置类并校验
        Class<?> configClass = configRegistrar.getConfigClassByKey(req.key());

        if (configClass != null) {
            PluginConfig annotation = configClass.getAnnotation(PluginConfig.class);
            if (annotation != null) {
                // 获取允许的作用域 (Arrays.asList 支持 Java 8+)
                List<ConfigManager.Scope> allowedScopes = Arrays.asList(annotation.scopes());

                if (!allowedScopes.contains(requestedScope)) {
                    return Result.error("此配置项不支持 %s 作用域，仅支持: %s".formatted(
                            requestedScope,
                            allowedScopes.stream().map(Enum::name).toList()
                    ));
                }
            }
        }

        String scopeId = req.scopeId() != null ? req.scopeId() : "default";
        configManager.set(
                requestedScope,
                scopeId,
                req.key(),
                req.value(),
                req.description(),
                req.group()
        );
        return Result.ok();
    }
}
