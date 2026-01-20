package com.github.winefoxbot.core.model.vo.webui.resp;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Builder
public record PluginConfigSchemaResponse (
        String pluginName,
        String description,
        List<String> allowedScopes,
        // 表单字段列表
        List<ConfigField> fields) {

    /**
     * @author FlanChan
     */
    @Data
    @Builder
    public static class ConfigField {
        private String key;          // 完整配置 Key (prefix.key)
        private String label;        // 显示名
        private String description;  // 提示信息

        /**
         * 字段类型: string, number, bool, select, array, map(可选)
         */
        private String type;

        /**
         * 子元素类型
         * 1. 当 type="array" 时，表示数组内元素的类型 (如 string, number)
         * 2. 当 type="map" 时，表示 Value 的类型 (Key 默认为 string)
         */
        private String itemType;

        private Object value;        // 当前值
        private Object defaultValue; // 默认值
        private List<Map<String, String>> options; // 下拉选项 (可选)
    }
}
