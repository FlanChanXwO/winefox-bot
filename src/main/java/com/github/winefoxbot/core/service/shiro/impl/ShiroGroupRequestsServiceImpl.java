package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroGroupRequestsMapper;
import com.github.winefoxbot.core.model.entity.ShiroGroupRequests;
import com.github.winefoxbot.core.service.shiro.ShiroGroupRequestsService;
import com.mikuac.shiro.dto.event.request.GroupAddRequestEvent;
import org.springframework.stereotype.Service;

/**
* @author FlanChan
* @description 针对表【shiro_group_requests】的数据库操作Service实现
* @createDate 2026-01-19 12:23:03
*/
@Service
public class ShiroGroupRequestsServiceImpl extends ServiceImpl<ShiroGroupRequestsMapper, ShiroGroupRequests>
    implements ShiroGroupRequestsService{

    @Override
    public boolean saveGroupAddRequest(GroupAddRequestEvent event) {
        return this.save(ShiroGroupRequests.convertToEntity(event));
    }
}




