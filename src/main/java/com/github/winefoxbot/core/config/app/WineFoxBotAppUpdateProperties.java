package com.github.winefoxbot.core.config.app;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Data
@ConfigurationProperties(prefix = "winefox.app.update")
public class WineFoxBotAppUpdateProperties {

    /**
     * 是否启用自动更新检查功能
     */
    private boolean enabled = true;

    /**
     * GitHub 仓库地址，格式为 "owner/repo"
     * 例如: "your-username/winefox-bot"
     */
    private String githubRepo;

    /**
     * GitHub API 地址模板，{repo} 会被替换为 githubRepo
     * {tag} 会被替换为 releaseTag
     */
    private String githubApiUrl = "https://api.github.com/repos/{repo}/releases/tags/{tag}";

    /**
     * 我们在 GitHub Actions 中设置的固定 Release 标签名
     */
    private String releaseTag = "latest";

    /**
     * GitHub 访问令牌（可选），用于提高 API 速率限制
     */
    private String githubToken;

    /**
     * 当前正在运行的 JAR 文件的名称。用于自我替换。
     * Spring Boot 会自动找到它，但提供一个明确的名称更可靠。
     */
    private String currentJarName;
}
