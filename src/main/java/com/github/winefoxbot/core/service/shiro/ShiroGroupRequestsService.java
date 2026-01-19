package com.github.winefoxbot.core.service.shiro;

import com.github.winefoxbot.core.model.entity.ShiroGroupRequests;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.request.GroupAddRequestEvent;

/**
* @author FlanChan
* @description 针对表【shiro_group_requests】的数据库操作Service
* @createDate 2026-01-19 12:23:03
*/
public interface ShiroGroupRequestsService extends IService<ShiroGroupRequests> {

    boolean saveGroupAddRequest(GroupAddRequestEvent event);
}
