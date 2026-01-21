package com.github.winefoxbot.plugins.fortune.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "winefoxbot.plugins.fortune")
@Validated
public class FortuneApiConfig {

    /**
     * 图片API类型，可选: wr, lolicon, local, custom, none
     * 如果选 custom，则使用下面的 customApi 配置
     */
    private String api = "none";

    /**
     * 自定义API配置 (当 api = custom 时生效)
     */
    private CustomApiConfig customApi = new CustomApiConfig();

    /**
     * 运势标题列表 (对应8个等级)
     */
    private List<String> jrysTitles = List.of("凶", "末吉", "末小吉", "小吉", "中吉", "吉", "大吉", "超大吉");

    private String jrysExtraMessage = "";

    private boolean autoRefreshJrys = true;

    /**
     * 运势文案列表 (对应8个等级)
     */
    private List<String> jrysMessages = List.of(
            "长夜再暗，火种仍在，转机终会到来。",
            "微光不灭，步步向前，黎明就在眼前。",
            "心怀希冀，顺流而行，好事悄然靠近。",
            "逆境翻篇，机遇迎面，惊喜不期而至。",
            "小吉随身，难题化易，幸运与你并肩。",
            "吉星高照，所行皆坦，所愿皆如愿。",
            "福泽深厚，大吉加身，一路花开有声。",
            "七星同耀，奇迹频现，今日万事皆成。"
    );



    // --- 内部配置类 ---

    @Data
    public static class CustomApiConfig {
        /**
         * API 请求地址
         */
        private String url;

        /**
         * 响应类型: JSON 或 IMAGE (直接返回图片流)
         */
        private ResponseType responseType = ResponseType.JSON;

        /**
         * JSON路径，用于从API响应中提取图片URL
         * 例如: $.data[0].url 或 $.url
         */
        private String jsonPath;

        /**
         * 请求参数配置
         */
        private Params params = new Params();
    }

    public enum ResponseType {
        JSON,
        IMAGE
    }

    @Data
    public static class Params {
        /**
         * 任何自定义的固定参数，例如: key=tag, value=BlueArchive
         */
        private List<ParamItem> staticParams;
    }

    @Data
    public static class ParamItem {
        private String key;
        private String value;
    }
}
