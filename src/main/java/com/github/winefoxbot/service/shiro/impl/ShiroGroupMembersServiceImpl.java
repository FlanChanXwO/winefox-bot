package com.github.winefoxbot.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.mapper.ShiroGroupMembersMapper;
import com.github.winefoxbot.model.dto.shiro.GroupMemberInfo;
import com.github.winefoxbot.model.entity.ShiroGroupMember;
import com.github.winefoxbot.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.utils.BotUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.notice.GroupAdminNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupCardChangeNoticeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【shiro_group_members】的数据库操作Service实现
* @createDate 2025-12-20 07:46:49
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class ShiroGroupMembersServiceImpl extends ServiceImpl<ShiroGroupMembersMapper, ShiroGroupMember>
    implements ShiroGroupMembersService{
    private final BotContainer botContainer;

    @Override
    public void saveOrUpdateGroupMemberInfo(GroupMessageEvent event) {
        saveOrUpdateGroupMemberInfo(extractGroupMember(event.getGroupId(), event.getUserId()));
    }

    @Override
    public void saveOrUpdateGroupMemberInfo(GroupAdminNoticeEvent event) {
        saveOrUpdateGroupMemberInfo(extractGroupMember(event.getGroupId(), event.getUserId()));
    }

    @Override
    public void saveOrUpdateGroupMemberInfo(GroupCardChangeNoticeEvent event) {
        saveOrUpdateGroupMemberInfo(extractGroupMember(event.getGroupId(), event.getUserId()));
    }

    private void saveOrUpdateGroupMemberInfo(ShiroGroupMember newMemberData) {
        LambdaQueryWrapper<ShiroGroupMember> queryWrapper = this.lambdaQuery().getWrapper()
                .eq(ShiroGroupMember::getGroupId, newMemberData.getGroupId())
                .eq(ShiroGroupMember::getUserId, newMemberData.getUserId());
        ShiroGroupMember existingMember = this.getOne(queryWrapper);
        if (existingMember != null) {
            existingMember.setMemberNickname(newMemberData.getMemberNickname());
            existingMember.setRole(newMemberData.getRole());
            LambdaUpdateWrapper<ShiroGroupMember> wrapper = this.lambdaUpdate().getWrapper()
                    .eq(ShiroGroupMember::getGroupId, existingMember.getGroupId())
                    .eq(ShiroGroupMember::getUserId, existingMember.getUserId());
            this.getBaseMapper().update(existingMember, wrapper);
        } else {
            this.save(newMemberData);
        }
    }

    private ShiroGroupMember extractGroupMember(Long groupId, Long userId) {
        ShiroGroupMember member = new ShiroGroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        Optional<Bot> bot = botContainer.robots.values().stream().findFirst();
        if (bot.isPresent()) {
            Bot firstBot = bot.get();
            GroupMemberInfo groupMemberInfo = BotUtils.getGroupMemberInfo(firstBot, groupId, userId);
            member.setRole(groupMemberInfo.getRole());
            member.setMemberNickname(groupMemberInfo.getCard().isBlank() ? groupMemberInfo.getNickname() : groupMemberInfo.getCard());
        } else {
            throw new RuntimeException("No bot available to fetch user nickname");
        }
        return member;
    }

    @Override
    public ShiroGroupMember getGroupMemberInfo(Long groupId, Long userId) {
        LambdaQueryWrapper<ShiroGroupMember> queryWrapper = this.lambdaQuery().getWrapper()
                .eq(ShiroGroupMember::getGroupId, groupId)
                .eq(ShiroGroupMember::getUserId, userId);
        return this.getOne(queryWrapper);
    }

    @Override
    public void deleteGroupMemberInfo(Long groupId, Long userId) {
        LambdaQueryWrapper<ShiroGroupMember> queryWrapper = this.lambdaQuery().getWrapper()
                .eq(ShiroGroupMember::getGroupId, groupId)
                .eq(ShiroGroupMember::getUserId, userId);
        this.remove(queryWrapper);
    }
}





