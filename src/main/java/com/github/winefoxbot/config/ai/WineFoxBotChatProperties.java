package com.github.winefoxbot.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "winefox.ai.chat")
public class WineFoxBotChatProperties {

}
