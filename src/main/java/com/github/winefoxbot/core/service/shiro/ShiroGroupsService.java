package com.github.winefoxbot.core.service.shiro;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.entity.ShiroGroup;

/**
* @author FlanChan
* @description 针对表【shiro_groups】的数据库操作Service
* @createDate 2025-12-20 07:46:49
*/
public interface ShiroGroupsService extends IService<ShiroGroup> {

    void deleteGroupInfo(Long groupId, Long selfId);
}
