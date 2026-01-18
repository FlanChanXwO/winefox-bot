package com.github.winefoxbot.core.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "winefoxbot.webui.admin")
public class WebUIAdmin {
    private String username;
    private String password;
}