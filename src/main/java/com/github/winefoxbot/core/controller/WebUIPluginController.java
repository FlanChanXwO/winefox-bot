package com.github.winefoxbot.core.controller;

import com.github.winefoxbot.core.annotation.plugin.PluginConfig;
import com.github.winefoxbot.core.init.ConfigMetadataRegistrar;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.vo.common.Result;
import com.github.winefoxbot.core.model.vo.webui.req.config.UpdateConfigRequest;
import com.github.winefoxbot.core.model.vo.webui.resp.PluginConfigSchemaResponse;
import com.github.winefoxbot.core.model.vo.webui.resp.PluginListItemResponse;
import com.github.winefoxbot.core.service.plugin.PluginService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 插件管理控制器
 * 处理插件列表显示、筛选、开关及配置动态设置
 * @author FlanChan
 */
@RestController
@RequestMapping("/api/plugins")
@RequiredArgsConstructor
public class WebUIPluginController {

    private final PluginService pluginService;
    private final ConfigManager configManager;
    private final ConfigMetadataRegistrar configRegistrar;

    /**
     * 1. 获取插件列表
     * @param keyword 搜索关键词 (可选)
     */
    @GetMapping("/list")
    public Result<List<PluginListItemResponse>> getPlugins(@RequestParam(required = false) String keyword) {
        return Result.ok(pluginService.getPluginList(keyword));
    }

    /**
     * 2. 切换插件开启/关闭
     */
    @PostMapping("/{pluginId}/toggle")
    public Result<Void> togglePlugin(
            @PathVariable String pluginId,
            @RequestParam boolean enable) {
        
        pluginService.togglePlugin(pluginId, enable);
        return Result.ok();
    }

    /**
     * 3. 获取插件的配置定义 (Schema) 和当前值
     * 前端点击 "配置" 按钮时调用此接口，动态生成表单
     */
    @GetMapping("/{pluginId}/config-schema")
    public Result<PluginConfigSchemaResponse> getPluginConfig(@PathVariable String pluginId) {
        try {
            return Result.ok(pluginService.getPluginConfigSchema(pluginId));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 4. 保存插件配置 (Global Scope)
     * 复用通用的 Config 逻辑，或者针对插件做特化
     */
    @PostMapping("/config/save")
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


    /**
     * 5. 获取配置列表 (支持按作用域筛选)
     * 例如: /api/plugins/config/list?scope=GROUP&scopeId=123456
     */
    @GetMapping("/config/list")
    public Result<List<Map<String, Object>>> getConfigList(
            @RequestParam(defaultValue = "GLOBAL") String scope,
            @RequestParam(required = false) String scopeId) {

        try {
            ConfigManager.Scope scopeEnum = ConfigManager.Scope.valueOf(scope.toUpperCase());

            // 如果是 GLOBAL，且前端没传 ID，我们可以强制给个 default，因为 Global 通常只有 default
            // 但如果是 GROUP/USER，没传 ID 意味着“查全部”
            String finalScopeId = scopeId;
            if (scopeEnum == ConfigManager.Scope.GLOBAL && (scopeId == null || scopeId.isBlank())) {
                finalScopeId = "default";
            }

            return Result.ok(pluginService.getPluginConfigList(scopeEnum, finalScopeId));
        } catch (IllegalArgumentException e) {
            return Result.error("无效的 Scope 类型");
        }
    }

    /**
     * 6. 删除/重置配置
     * 删除后，该项将回退到下一级作用域或默认值
     */
    @DeleteMapping("/config/delete")
    public Result<Void> deleteConfig(
            @RequestParam String key,
            @RequestParam(defaultValue = "GLOBAL") String scope,
            @RequestParam(defaultValue = "default") String scopeId) {

        pluginService.deleteConfig(key, scope, scopeId);
        return Result.ok();
    }

    /**
     * 7. 重置整组配置
     */
    @DeleteMapping("/config/reset-scope")
    public Result<Void> resetPluginScope(
            @RequestParam String pluginId,
            @RequestParam String scope,
            @RequestParam String scopeId) {

        pluginService.deleteConfigByScope(pluginId, scope, scopeId);
        return Result.ok();
    }

}
