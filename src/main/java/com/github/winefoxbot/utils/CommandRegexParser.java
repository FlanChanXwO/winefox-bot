package com.github.winefoxbot.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.*;

public class CommandRegexParser {

    /**
     * 从类似 "(cmd1|cmd2|cmd3)" 的模式中提取所有命令。
     *
     * @param regex 正则表达式字符串
     * @return 提取出的命令列表，如果无法匹配则返回空列表。
     */
    public static List<String> extractCommands(String regex) {
        if (regex == null || regex.isEmpty()) {
            return List.of();
        }

        List<String> commands = new ArrayList<>();
        System.out.println(regex);
        
        // 1. 定位括号内的核心部分，例如 "(update|更新版本)"
        // 这个正则表达式会匹配最内层的括号对
        Pattern pattern = Pattern.compile("\\(([^()|]+(?:\\|[^()|]+)+)\\)");
        Matcher matcher = pattern.matcher(regex);

        if (matcher.find()) {
            // 2. 提取括号内的内容，例如 "update|更新版本"
            String groupContent = matcher.group(1);
            
            // 3. 使用 "|" 分割字符串，得到所有命令
            String[] commandParts = groupContent.split("\\|");
            for (String cmd : commandParts) {
                if (cmd != null && !cmd.trim().isEmpty()) {
                    commands.add(COMMAND_PREFIX + cmd.trim() + COMMAND_SUFFIX);
                }
            }
        }
        
        return commands;
    }


    public static void main(String[] args) {
        String commandRegex = COMMAND_PREFIX_REGEX + "(help|h|wf帮助|帮助)(?:\\s+(.+))?" + COMMAND_SUFFIX_REGEX;
        System.out.println(extractCommands(commandRegex));
    }
}
