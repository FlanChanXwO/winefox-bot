package com.github.winefoxbot.core.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.winefoxbot.core.model.entity.ShiroFriends;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.vo.webui.resp.FriendAndGroupStatsResponse;
import com.github.winefoxbot.core.service.shiro.ShiroFriendsService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:18
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/friend-group")
public class WebUIFriendAndGroupController {
    private final ShiroFriendsService friendsService;
    private final ShiroGroupsService groupsService;

    @GetMapping("/stats/{botId}")
    public FriendAndGroupStatsResponse getFriendAndGroupStats(@PathVariable Long botId) {
        long friendCount = friendsService.count(new LambdaQueryWrapper<>(ShiroFriends.class).eq(ShiroFriends::getBotId,botId));
        long groupCount = groupsService.count(new LambdaQueryWrapper<>(ShiroGroup.class).eq(ShiroGroup::getSelfId,botId));
        return new FriendAndGroupStatsResponse(friendCount, groupCount);
    }
}