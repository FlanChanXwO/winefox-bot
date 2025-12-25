package com.github.winefoxbot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 帮助文档对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HelpDoc {
    /**
     * 功能分组
     */
    private String group;
    /**
     * 功能名称
     */
    private String name;
    /**
     * 功能描述
     */
    private String description;
    /**
     * 所需权限
     */
    private String permission;
    /**
     * 命令列表（包含别名）
     */
    private List<String> commands;

    // 辅助方法，用于格式化显示
    public String getFormattedCommands() {
        return commands.isEmpty() ? "无" : String.join(", ", commands);
    }
}
