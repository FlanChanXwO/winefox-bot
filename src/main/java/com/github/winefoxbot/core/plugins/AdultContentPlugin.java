package com.github.winefoxbot.core.plugins;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.constants.ConfigConstants;
import com.github.winefoxbot.core.manager.ConfigManager;
import com.github.winefoxbot.core.model.enums.Permission;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

import static com.github.winefoxbot.core.config.app.WineFoxBotConfig.*;

@Plugin(
        name = "配置管理",
        permission = Permission.ADMIN, // 默认需要管理员权限
        iconPath = "icon/配置.png",
        order = 99
)
@Slf4j
@RequiredArgsConstructor
public class AdultContentPlugin {

    private final ConfigManager configManager;

    @PluginFunction(
            name = "设置福利内容模式",
            description = "设置本会话（群聊或私聊）的福利图片内容模式。可用模式: sfw (安全), r18 (仅成人), mix (混合)",
            commands = {
                    COMMAND_PREFIX + "福利模式 sfw" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "福利模式 r18" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "福利模式 mix" + COMMAND_SUFFIX
            }
    )
    @AnyMessageHandler // 使用 AnyMessageHandler 捕获所有消息
    @MessageHandlerFilter(cmd = COMMAND_PREFIX_REGEX + "福利模式\\s+(sfw|r18|mix)" + COMMAND_SUFFIX_REGEX)
    public void setContentMode(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String mode = matcher.group(1).toLowerCase(); // 获取 sfw/r18/mix
        boolean isGroup = event.getGroupId() != null;


        // 1. 准备回复信息
        String modeDesc = switch (mode) {
            case ConfigConstants.AdultContent.MODE_SFW -> "✅ 安全 (SFW)";
            case ConfigConstants.AdultContent.MODE_R18 -> "🔞 仅成人 (R18)";
            case ConfigConstants.AdultContent.MODE_MIX -> "🔄 混合模式";
            default -> "未知"; // 理论上不会发生，因为正则已限制
        };

        // 2. 判断消息类型，并执行相应的配置设置
        if (isGroup) {
            // --- 群聊场景 ---
            String groupId = String.valueOf(event.getGroupId());
            configManager.set(
                    ConfigManager.Scope.GROUP,
                    groupId,
                    ConfigConstants.AdultContent.SETU_CONTENT_MODE,
                    mode,
                    "[福利内容] 设置图片内容模式",
                    ConfigConstants.AdultContent.GROUP_ADULT_CONTENT
            );
            String reply = String.format("本群的福利内容模式已设置为：%s", modeDesc);
            log.info("群组 {} 的内容模式被用户 {} 设置为: {}", groupId, event.getUserId(), mode);
            bot.sendMsg(event, reply, false);

        } else  {
            // --- 私聊场景 ---
            String userId = String.valueOf(event.getUserId());
            configManager.set(
                    ConfigManager.Scope.USER,
                    userId,
                    ConfigConstants.AdultContent.SETU_CONTENT_MODE,
                    mode,
                    "[福利内容] 设置图片内容模式",
                    ConfigConstants.AdultContent.GROUP_ADULT_CONTENT
            );
            String reply = String.format("您的福利内容模式已设置为：%s", modeDesc);
            log.info("用户 {} 的私聊内容模式设置为: {}", userId, mode);
            bot.sendMsg(event, reply, false);
        }
    }

    @PluginFunction(
            name = "R18自动撤回开关",
            description = "开启或关闭R18消息的自动撤回功能。用法：命令 + on/off 或 开启/关闭",
            commands = {
                    COMMAND_PREFIX + "瑟瑟撤回 on" + COMMAND_SUFFIX,
                    COMMAND_PREFIX + "瑟瑟撤回 off" + COMMAND_SUFFIX
            }
    )
    @GroupMessageHandler
    @MessageHandlerFilter(cmd = COMMAND_PREFIX_REGEX + "瑟瑟撤回\\s+(on|off|开启|关闭)" + COMMAND_SUFFIX_REGEX)
    public void toggleAutoRevoke(Bot bot, GroupMessageEvent event, Matcher matcher) {
        String groupId = String.valueOf(event.getGroupId());
        String action = matcher.group(1).toLowerCase();
        boolean enable = action.equals("on") || action.equals("开启");

        configManager.set(
                ConfigManager.Scope.GROUP,
                groupId,
                ConfigConstants.AdultContent.ADULT_AUTO_REVOKE_ENABLED,
                enable,
                "[成人内容] 是否自动撤回R18消息",
                ConfigConstants.AdultContent.GROUP_ADULT_CONTENT
        );

        String status = enable ? "✅ 已开启" : "❌ 已关闭";
        String reply = String.format("R18消息自动撤回功能已设置为：%s", status);
        log.info("群组 {} 的R18自动撤回被用户 {} 设置为: {}", groupId, event.getUserId(), enable);
        bot.sendGroupMsg(event.getGroupId(), reply, false);
    }


    @PluginFunction(
            name = "设置R18撤回延迟",
            description = "设置R18消息自动撤回的延迟时间（单位：秒）。用法：命令 + 秒数",
            commands = {COMMAND_PREFIX + "瑟瑟撤回延迟 60" + COMMAND_SUFFIX}
    )
    @GroupMessageHandler // 此功能仅限群聊
    @MessageHandlerFilter(cmd = COMMAND_PREFIX_REGEX + "瑟瑟撤回延迟\\s+(\\d+)" + COMMAND_SUFFIX_REGEX)
    public void setRevokeDelay(Bot bot, GroupMessageEvent event, Matcher matcher) {
        Long groupId = event.getGroupId();
        String delayStr = matcher.group(1);
        int delay;
        try {
            delay = Integer.parseInt(delayStr);
            if (delay < 10 || delay > 300) { // 设置一个合理的范围，例如10秒到5分钟
                bot.sendGroupMsg(groupId, "❌ 设置失败，延迟时间必须在 10 到 300 秒之间。", false);
                return;
            }
        } catch (NumberFormatException e) {
            bot.sendGroupMsg(groupId, "❌ 无效的数字格式。", false);
            return;
        }

        configManager.set(
                ConfigManager.Scope.GROUP,
                String.valueOf(groupId),
                ConfigConstants.AdultContent.ADULT_REVOKE_DELAY_SECONDS,
                delay,
                "[福利内容] R18消息自动撤回延迟（秒）",
                ConfigConstants.AdultContent.GROUP_ADULT_CONTENT
        );

        String reply = String.format("✅ 操作成功！本群的R18消息自动撤回延迟已设置为 %d 秒。", delay);
        log.info("群组 {} 的R18撤回延迟被用户 {} 设置为: {} 秒", groupId, event.getUserId(), delay);
        bot.sendGroupMsg(event.getGroupId(), reply, false);
    }

}
