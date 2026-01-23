package com.github.winefoxbot.core.config.status;

import lombok.Getter;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author FlanChan
 */
@Component
@Getter
public class StatusImageGeneratorConfig {


    private final String botName;
    private final String dashboardName;
    private final String projectName;
    private final String projectVersion;
    
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
        this.projectName = properties.getProjectName();
        this.projectVersion = buildProperties.map(BuildProperties::getVersion).orElse("dev");
    }

}
