package com.github.winefoxbot.core.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.winefoxbot.core.model.entity.ShiroFriends;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.model.entity.ShiroUser;
import com.github.winefoxbot.core.model.vo.webui.resp.FriendAndGroupStatsResponse;
import com.github.winefoxbot.core.service.shiro.ShiroFriendsService;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import com.github.winefoxbot.core.service.shiro.ShiroUsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-18-21:18
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class WebUIUserController {
    private final ShiroUsersService usersService;

    @GetMapping("/userids")
    public List<Long> getUserIdsList() {
        return usersService.list().stream().map(ShiroUser::getUserId).toList();
    }
}