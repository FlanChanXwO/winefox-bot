package com.github.winefoxbot.plugins;

import com.github.winefoxbot.model.dto.dnateam.*;
import com.github.winefoxbot.service.dnateam.DnaTeamService;
import com.mikuac.shiro.annotation.GroupMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.common.utils.ShiroUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import org.jsoup.internal.StringUtil;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-24-17:44
 */
@Shiro
@Component
@RequiredArgsConstructor
public class DNATeamPlugin {
    private final DnaTeamService dnaTeamService;

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/发起组队$")
    public void handleCreateTeam(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long userId = event.getUserId();

        DnaTeamCreateResult result = dnaTeamService.createTeam(groupId, userId);

        if (!result.isSuccess()) {
            bot.sendGroupMsg(groupId, result.getMessage(),false);
            return;
        }

        bot.sendGroupMsg(groupId,
                "🎮 组队成功！\n" +
                        "队长：" + event.getSender().getNickname() + "\n" +
                        "当前人数：1 / 4\n" +
                        "发送【/加入组队】即可加入",false);
    }

    /* ==================== 加入组队 ==================== */

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/加入组队$")
    public void handleJoinTeam(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long userId = event.getUserId();

        DnaTeamJoinResult result = dnaTeamService.joinTeam(groupId, userId);

        if (!result.isSuccess()) {
            bot.sendGroupMsg(groupId, result.getMessage(),false);
            return;
        }

        if (result.isFull()) {
            bot.sendGroupMsg(groupId,
                    "✅ 你已加入队伍！\n" +
                            "🎉 队伍已满员（4 / 4），可以开始啦！",false);
        } else {
            bot.sendGroupMsg(groupId,
                    "✅ 你已加入队伍！\n" +
                            "当前人数：" + result.getCurrentCount() + " / 4",false);
        }
    }

    /* ==================== 退出组队 ==================== */

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/退出组队$")
    public void handleLeaveTeam(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long userId = event.getUserId();

        DnaTeamLeaveResult result = dnaTeamService.leaveTeam(groupId, userId);

        if (!result.isSuccess()) {
            bot.sendGroupMsg(groupId, result.getMessage(),false);
            return;
        }

        bot.sendGroupMsg(groupId,
                "🚪 已退出队伍\n" +
                        "当前人数：" + result.getCurrentCount() + " / 4",false);
    }

    /* ==================== 解散组队（队长） ==================== */

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/解散组队$")
    public void handleDismissTeam(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long userId = event.getUserId();

        DnaTeamCommonResult result = dnaTeamService.dismissTeam(groupId, userId);

        if (!result.isSuccess()) {
            bot.sendGroupMsg(groupId, result.getMessage(),false);
            return;
        }

        bot.sendGroupMsg(groupId, "🧨 队伍已解散",false);
    }

    /* ==================== 查看组队 ==================== */

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/查看组队$")
    public void handleViewTeam(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        Long userId = event.getUserId();

        Optional<DnaTeamView> teamOpt = dnaTeamService.getTeamView(groupId,userId);

        if (teamOpt.isEmpty()) {
            bot.sendGroupMsg(groupId, "❌ 当前没有进行中的组队",false);
            return;
        }

        DnaTeamView team = teamOpt.get();

        StringBuilder sb = new StringBuilder();
        sb.append("👥 当前组队情况\n");
        sb.append("人数：").append(team.getMemberCount()).append(" / 4\n");
        sb.append("状态：").append(team.isFull() ? "已满" : "未满").append("\n\n");

        int i = 1;
        for (DnaTeamMemberView member : team.getMembers()) {
            sb.append(i++).append(". ")
                    .append(member.getNickname());
            if (member.isLeader()) {
                sb.append("（队长）");
            }
            sb.append("\n");
        }

        bot.sendGroupMsg(groupId, sb.toString(),false);
    }

    /* ==================== 队长踢人 ==================== */

    @GroupMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/踢出组队$")
    public void handleKickMember(Bot bot, GroupMessageEvent event) {
        Long groupId = event.getGroupId();
        Long operatorId = event.getUserId();

        // 解析 @ 的目标用户
        List<Long> atList = ShiroUtils.getAtList(event.getArrayMsg());

        if (atList.isEmpty()) {
            bot.sendGroupMsg(groupId, "❌ 请 @ 要踢出的成员",false);
            return;
        }

        if (atList.contains(operatorId)) {
            bot.sendGroupMsg(groupId, "❌ 不能踢出自己",false);
            return;
        }



        DnaTeamView dnaTeamView = dnaTeamService.getMyTeam(groupId, operatorId).orElseGet(() -> {
            bot.sendGroupMsg(groupId, "❌ 你当前不在任何队伍中", false);
            return null;
        });

        if (dnaTeamView == null) {
            return;
        }

        if (!atList.stream().allMatch(id -> dnaTeamView.getMembers().stream().anyMatch(member -> member.getUserId() == id))) {
            bot.sendGroupMsg(groupId, "❌ 存在不在队伍中的成员", false);
            return;
        }

        MsgUtils builder = MsgUtils.builder()
                .at(operatorId);

        for (Long targetUserId : atList) {
            dnaTeamService.kickMember(groupId, operatorId, targetUserId);
            builder.at(targetUserId);
        }

        Optional<DnaTeamView> myTeam = dnaTeamService.getMyTeam(groupId, operatorId);

        DnaTeamView result = myTeam.orElseThrow();

        String msg = builder
                .text("🦵 成员已被移出队伍\n" +
                        "被踢成员：" + StringUtil.join(result.getMembers().stream().map(DnaTeamMemberView::getNickname).toList(), ",") + "\n" +
                        "当前人数：" + result.getMemberCount() + " / 4").build();
        bot.sendGroupMsg(groupId, msg, false);
    }

}