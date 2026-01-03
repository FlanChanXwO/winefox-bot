package com.github.winefoxbot.config.reply;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-02-17:21
 */
@Data
@ConfigurationProperties(prefix = "winefox.reply.voice")
public class WinefoxBotVoiceReplyProperties {
    private Boolean enabled = true;

    private String voiceRootDir = "resources/reply/voices";

    public Path getVoiceRootPath() {
        return Path.of(voiceRootDir);
    }
}