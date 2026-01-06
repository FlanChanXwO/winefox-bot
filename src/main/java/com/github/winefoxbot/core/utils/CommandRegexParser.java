package com.github.winefoxbot.core.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_PREFIX;
import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.COMMAND_SUFFIX;

public class CommandRegexParser {

    // 正则表达式，用于匹配 (选项1|选项2|...) 这样的分组
    // 它会匹配最内层的括号，以确保正确处理嵌套情况（虽然当前逻辑是顺序处理）
    private static final Pattern GROUP_PATTERN = Pattern.compile("\\(([^()|]+(?:\\|[^()|]+)+)\\)");

    // 定义一个占位符，用于临时替换匹配到的分组
    private static final String PLACEHOLDER = "@@PLACEHOLDER@@";

    /**
     * 从包含选择分组的正则表达式模板中，生成所有可能的具体命令。
     * 例如：从 "^/(开启|关闭)瑟瑟自动撤回$" 生成 ["/开启瑟瑟自动撤回", "/关闭瑟瑟自动撤回"]
     *
     * @param regexTemplate 正则表达式模板字符串
     * @return 所有可能的命令列表
     */
    public static List<String> generateCommands(String regexTemplate) {
        if (regexTemplate == null || regexTemplate.isEmpty()) {
            return Collections.emptyList();
        }

        // 移除正则表达式的开头^, 结尾$ 以及命令本身可能带的前缀/
        String cleanTemplate = regexTemplate.replaceAll("^\\^/?|\\$$", "");

        List<List<String>> allOptions = new ArrayList<>();
        Matcher matcher = GROUP_PATTERN.matcher(cleanTemplate);

        // 1. 提取出所有 (a|b|c) 分组的选项
        while (matcher.find()) {
            String groupContent = matcher.group(1); // 获取括号内的内容，如 "开启|关闭"
            allOptions.add(List.of(groupContent.split("\\|")));
        }

        // 2. 将模板中的 (a|b|c) 替换为占位符
        String templateWithPlaceholders = matcher.replaceAll(PLACEHOLDER);

        // 如果没有找到任何分组，说明它就是一个普通命令
        if (allOptions.isEmpty()) {
            // 需要去除模板中可能存在的其他正则元字符，如 (?:...) 等
            String finalCommand = cleanSimpleRegex(templateWithPlaceholders);
            return List.of(COMMAND_PREFIX + finalCommand.trim() + COMMAND_SUFFIX);
        }

        // 3. 递归（或迭代）地将选项组合起来，生成最终命令
        List<String> combinedCommands = new ArrayList<>();
        // 初始状态，只有一个包含占位符的模板
        combinedCommands.add(templateWithPlaceholders);

        for (List<String> options : allOptions) {
            List<String> nextLevelCommands = new ArrayList<>();
            for (String command : combinedCommands) {
                for (String option : options) {
                    // replaceFirst 只替换第一个出现的占位符
                    nextLevelCommands.add(command.replaceFirst(PLACEHOLDER, Matcher.quoteReplacement(option)));
                }
            }
            combinedCommands = nextLevelCommands;
        }

        // 4. 添加命令前缀和后缀
        List<String> result = new ArrayList<>();
        for (String cmd : combinedCommands) {
            // 清理掉剩余的正则元字符，例如 (?:...)
            String finalCommand = cleanSimpleRegex(cmd);
            if (!finalCommand.trim().isEmpty()) {
                result.add(COMMAND_PREFIX + finalCommand.trim() + COMMAND_SUFFIX);
            }
        }

        return result;
    }

    /**
     * 清理命令字符串中的正则表达式元字符，以获得纯净的、适合展示的命令文本。
     * @param regex 命令的一部分，可能含有正则表达式
     * @return 清理后的字符串
     */
    private static String cleanSimpleRegex(String regex) {
        if (regex == null) {
            return "";
        }
        String cleaned = regex;

        // 步骤1: 循环移除最内层的括号及其内容，直到没有括号为止。
        while (cleaned.contains("(")) {
            cleaned = cleaned.replaceAll("\\([^()]*\\)", "");
        }

        // 步骤2:【顺序调整】优先处理复合的元字符表达式，例如 \s+ 或 \s*。
        // 将它们直接移除，避免留下 \s。
        cleaned = cleaned.replaceAll("\\\\s[+*]", "");

        // 步骤3:【顺序调整】然后才移除所有独立的正则量词。
        // 这样就不会错误地破坏上一步的匹配。
        cleaned = cleaned.replaceAll("[?*+]", "");

        // 步骤4: 清理首尾空白。
        return cleaned.trim();
    }
}
