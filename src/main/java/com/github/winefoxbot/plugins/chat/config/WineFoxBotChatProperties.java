package com.github.winefoxbot.plugins.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "winefox.ai.chat")
public class WineFoxBotChatProperties {

}
