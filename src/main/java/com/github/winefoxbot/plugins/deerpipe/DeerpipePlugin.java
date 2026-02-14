package com.github.winefoxbot.plugins.deerpipe;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.config.app.WineFoxBotRobotProperties;
import com.github.winefoxbot.core.model.enums.common.Permission;
import com.github.winefoxbot.core.util.BotUtil;
import com.github.winefoxbot.plugins.deerpipe.config.DeerPipePluginConfig;
import com.github.winefoxbot.plugins.deerpipe.model.dto.BatchTarget;
import com.github.winefoxbot.plugins.deerpipe.service.DeerService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.AtEnum;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * @author FlanChan
 */
@Plugin(name = "🦌管",description = "可以🦌", order = 4, permission = Permission.USER,  iconPath = "icon/鹿.png" ,config = DeerPipePluginConfig.class)
@RequiredArgsConstructor
@Slf4j
public class DeerpipePlugin {
    private final DeerService deerService;
    private final WineFoxBotRobotProperties robotProperties;

    @PluginFunction(name = "鹿管", description = "每日签到", commands = {"鹿|🦌", "/鹿|/🦌"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text,at = AtEnum.NOT_NEED, cmd = "^/?[鹿🦌]$")
    public void deerSelf(Bot bot, AnyMessageEvent event) {
        long userId = event.getUserId();
        String avatarUrl = getAvatarUrl(userId);
        byte[] img = deerService.attend(userId, avatarUrl);
        bot.sendMsg(event, MsgUtils.builder().text("成功🦌了").img(img).build(), false);
    }

    @PluginFunction(name = "允许/禁止被帮鹿", description = "设置自己是否允许被别人帮鹿", commands = {"/允许被鹿", "/禁止被鹿", "/允许被🦌", "/禁止被🦌"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, at = AtEnum.NOT_NEED, cmd = "^/(允许|禁止)被[鹿🦌]$")
    public void setSelfHelpStatus(Bot bot, AnyMessageEvent event, Matcher matcher) {
        boolean allow = "允许".equals(matcher.group(1));
        deerService.setAllowHelpStatus(event.getUserId(), allow);
        bot.sendMsg(event, allow ? "已开启，现在别人可以帮你🦌了~" : "已关闭，现在只有你自己能🦌了！", false);
    }

    @PluginFunction(name = "管理设置被帮状态", description = "在群里管理员设置他人状态，例如：/设置被鹿 开 @111 @333", permission = Permission.ADMIN, commands = "/设置被鹿|设置被🦌 [开/关] @某人 ")
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.at, cmd = "^(?:/?设置被[鹿🦌])\\s+(开|关|on|off)\\s*(?:\\[CQ:at,.*?\\]\\s*)+$")
    public void setOtherHelpStatus(Bot bot, GroupMessageEvent event, Matcher matcher) {
        List<Long> atList = ShiroUtils.getAtList(event.getArrayMsg());
        if (atList.isEmpty()) return;
        String command = matcher.group(1);
        boolean allow = command.matches("开|on");
        List<String> logs = new ArrayList<>();

        long operatorId = event.getUserId();
        boolean operatorIsSuper = robotProperties.getSuperUsers().contains(operatorId);

        for (Long targetId : atList) {
            // 2. 检查目标权限：不能给管理员/群主设置，除非操作者是超管
            if (!operatorIsSuper && (BotUtil.isAdmin(bot, targetId) || robotProperties.getSuperUsers().contains(targetId))) {
                continue;
            }

            deerService.setAllowHelpStatus(targetId, allow);
            logs.add("用户 " + BotUtil.getGroupMemberNickname(bot, event.getGroupId(), targetId) + " 被鹿策略设置为: " + (allow ? "允许" : "禁止"));
        }
        if (logs.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "没有成功设置任何用户，可能是因为目标用户是管理员或群主，或者是超级管理员", false);
            return;
        }
        bot.sendGroupMsg(event.getGroupId(), String.join("\n", logs), false);
    }


    @PluginFunction(name = "帮鹿", description = "在群里帮别人签到", commands = {"鹿|🦌 @某人", "/鹿|/🦌 @某人", "鹿|🦌 @全体成员", "/鹿|/🦌 @全体成员"})
    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.at, cmd = "^/?[鹿🦌]\\s*(?:\\[CQ:at,.*?\\]\\s*)+$")
    public void deerOther(Bot bot, GroupMessageEvent event) {
        List<Long> atList = ShiroUtils.getAtList(event.getArrayMsg());
        boolean atAll = ShiroUtils.isAtAll(event.getArrayMsg());

        // 目标列表
        List<BatchTarget> targets = new ArrayList<>();

        if (atAll) {
            var memberList = bot.getGroupMemberList(event.getGroupId());
            if (memberList != null && memberList.getData() != null) {
                targets = memberList.getData().stream()
                        .filter(m -> !Objects.equals(m.getUserId(), event.getSelfId()))
                        .map(m -> new BatchTarget(m.getUserId(), m.getNickname()))
                        .collect(Collectors.toList());
            }
        } else if (!atList.isEmpty()) {
            if (atList.contains(event.getSelfId())) {
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder()
                                .at(event.getUserId())
                                .text(StringUtils.SPACE + "不可以帮酒狐🦌哦~")
                        .build(), false);
                return;
            }
            for (Long targetId : atList) {
                String nickname = "用户" + targetId;
                try {
                    var info = bot.getGroupMemberInfo(event.getGroupId(), targetId, false);
                    if(info != null && info.getData() != null) nickname = info.getData().getNickname();
                } catch (Exception ignored) {}
                targets.add(new BatchTarget(targetId, nickname));
            }
        }

        if (targets.isEmpty()) return;

        // 分流处理
        if (targets.size() == 1) {
            BatchTarget target = targets.getFirst();
            try {
                // 使用带权限检查的接口
                String avatar = ShiroUtils.getUserAvatar(target.userId(), 640);
                byte[] img = deerService.attendByOther(target.userId(), target.nickname(), avatar);
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text("成功帮" + target.nickname() + "🦌了").img(img).build(), false);
            } catch (RuntimeException e) {
                // 捕获不允许被帮的异常
                log.error(e.getMessage());
                bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().at(event.getUserId()).text(StringUtils.SPACE + "帮🦌失败").build(), false);
            }
        } else {
            if (atAll) bot.sendGroupMsg(event.getGroupId(), "正在给所有人🦌管", false);
            byte[] img = deerService.batchAttendAndRender(targets);
            bot.sendGroupMsg(event.getGroupId(), MsgUtils.builder().text("多人运动结算如下：").img(img).build(), false);
        }
    }

    @PluginFunction(name = "补鹿", description = "补签本月日期，例如'补鹿 1'是给本月1号补，每天只有1次机会", commands = {"补鹿|补🦌 [日期]", "/补鹿|/补🦌 [日期]"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?(?:补鹿|补🦌)\\s*(\\d+)$")
    public void deerPast(Bot bot, AnyMessageEvent event, Matcher matcher) {
        int day = Integer.parseInt(matcher.group(1));
        long userId = event.getUserId();
        var result = deerService.attendPast(userId, day, getAvatarUrl(userId));
        bot.sendMsg(event, MsgUtils.builder().text(result.message()).img(result.image()).build(), false);
    }

    @PluginFunction(name = "鹿历", description = "查看日历", commands = {"鹿历|🦌历", "/鹿历|/🦌历"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?(?:鹿历|🦌历)$")
    public void deerCalendar(Bot bot, AnyMessageEvent event) {
        long userId = event.getUserId();
        byte[] img = deerService.viewCalendar(userId, getAvatarUrl(userId));
        bot.sendMsg(event, MsgUtils.builder().img(img).build(), false);
    }

    @PluginFunction(name = "上月鹿历", description = "查看上个月的日历", commands = {"上月鹿历|上月🦌历", "/上月鹿历|/上月🦌历"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?(?:上月鹿历|上月🦌历)$")
    public void deerLastMonthCalendar(Bot bot, AnyMessageEvent event) {
        long userId = event.getUserId();
        byte[] img = deerService.viewLastMonthCalendar(userId, getAvatarUrl(userId));
        bot.sendMsg(event, MsgUtils.builder().img(img).build(), false);
    }

    private String getAvatarUrl(long userId) {
        return ShiroUtils.getUserAvatar(userId,640);
    }
}