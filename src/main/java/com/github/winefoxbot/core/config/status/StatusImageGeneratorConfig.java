package com.github.winefoxbot.core.config.status;

import cn.hutool.core.util.RandomUtil;
import lombok.Getter;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Optional;

@Component
@Getter
public class StatusImageGeneratorConfig {

    private static final String DEFAULT_TRANSPARENT_IMAGE = "";

    private final String botName;
    private final String dashboardName;
    private final String projectName;
    private final String projectVersion;
    private final String htmlTemplatePath;
    private final String cssTemplatePath;
    
    private final StatusImageGeneratorProperties properties;
    
    /**
     * 使用构造注入，保证所有 Bean 都已准备好
     * BuildProperties 用于获取项目版本号，它由 spring-boot-maven-plugin 的 build-info goal 生成
     * 如果项目没有该插件，可以注入 ApplicationContext 手动获取或直接硬编码
     */
    public StatusImageGeneratorConfig(StatusImageGeneratorProperties properties, Optional<BuildProperties> buildProperties) {
        this.properties = properties;
        this.botName = properties.getBotName();
        this.dashboardName = properties.getDashboardName();
        this.htmlTemplatePath = properties.getHtmlTemplatePath();
        this.cssTemplatePath = properties.getCssTemplatePath();
        this.projectName = properties.getProjectName();
        this.projectVersion = buildProperties.map(BuildProperties::getVersion).orElse("dev");
    }

    /**
     * 根据配置获取人物立绘图片路径
     * @return 图片路径 (e.g., "classpath:images/char.png" or "D:/images/char.png")
     */
    public String getCharacterImagePath() {
        return getImagePath(properties.getCharacterImages());
    }
    
    /**
     * 根据配置获取顶部横幅图片路径
     * @return 图片路径
     */
    public String getTopBannerImagePath() {
        return getImagePath(properties.getTopBannerImages());
    }

    /**
     * 获取默认图片（透明）
     * @return Base64 Data URL
     */
    public String getDefaultImageBase64() {
        return DEFAULT_TRANSPARENT_IMAGE;
    }

    private String getImagePath(List<String> imageList) {
        if (CollectionUtils.isEmpty(imageList)) {
            return null; // 返回 null，由调用方处理默认值
        }
        if (properties.isRandomImage()) {
            return RandomUtil.randomEle(imageList);
        }
        return imageList.get(0);
    }
}
