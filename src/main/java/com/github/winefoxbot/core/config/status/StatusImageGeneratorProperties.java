// src/main/java/com/github/winefoxbot/config/properties/StatusImageGeneratorProperties.java
package com.github.winefoxbot.core.config.status;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "winefox.status")
public class StatusImageGeneratorProperties {

    /**
     * Bot 的名称
     */
    private String botName = "WineFoxBot";

    /**
     * 项目名称
     */
    private String projectName ="WineFoxBot";

    /**
     * Dashboard 的名称
     */
    private String dashboardName = "WineFoxBot Dashboard";

    /**
     * 人物立绘图片路径列表。
     * 可以是 classpath: 或文件系统路径。
     */
    private List<String> characterImages;

    /**
     * 顶部横幅图片路径列表。
     * 可以是 classpath: 或文件系统路径。
     */
    private List<String> topBannerImages;

    /**
     * 是否从列表中随机选择图片。
     * 如果为 false，则使用列表中的第一个元素。
     */
    private boolean randomImage = true;

    /**
     * HTML 模板文件的 Classpath 路径
     */
    private String htmlTemplatePath = "status/main";
    
    /**
     * CSS 样式文件的 Classpath 路径
     */
    private String cssTemplatePath = "classpath:templates/status/res/css/style.css";
}
