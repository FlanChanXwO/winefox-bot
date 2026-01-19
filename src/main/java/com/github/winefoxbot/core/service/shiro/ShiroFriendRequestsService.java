package com.github.winefoxbot.core.service.shiro;

import com.github.winefoxbot.core.model.entity.ShiroFriendRequests;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mikuac.shiro.dto.event.request.FriendAddRequestEvent;

/**
* @author FlanChan
* @description 针对表【shiro_friend_requests】的数据库操作Service
* @createDate 2026-01-19 12:23:03
*/
public interface ShiroFriendRequestsService extends IService<ShiroFriendRequests> {

    boolean saveFriendAddRequest(FriendAddRequestEvent event);
}
