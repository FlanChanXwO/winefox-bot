package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.model.entity.ShiroFriendRequests;
import com.github.winefoxbot.core.service.shiro.ShiroFriendRequestsService;
import com.github.winefoxbot.core.mapper.ShiroFriendRequestsMapper;
import com.mikuac.shiro.dto.event.request.FriendAddRequestEvent;
import org.springframework.stereotype.Service;

/**
* @author FlanChan
* @description 针对表【shiro_friend_requests】的数据库操作Service实现
* @createDate 2026-01-19 12:23:03
*/
@Service
public class ShiroFriendRequestsServiceImpl extends ServiceImpl<ShiroFriendRequestsMapper, ShiroFriendRequests>
    implements ShiroFriendRequestsService{


    @Override
    public boolean saveFriendAddRequest(FriendAddRequestEvent event) {
        return this.save(ShiroFriendRequests.convertToEntity(event));
    }



}




