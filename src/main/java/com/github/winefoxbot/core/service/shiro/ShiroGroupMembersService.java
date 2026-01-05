package com.github.winefoxbot.core.service.shiro;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.entity.ShiroGroupMember;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.notice.GroupAdminNoticeEvent;
import com.mikuac.shiro.dto.event.notice.GroupCardChangeNoticeEvent;

/**
* @author FlanChan
* @description 针对表【shiro_group_members】的数据库操作Service
* @createDate 2025-12-20 07:46:49
*/
public interface ShiroGroupMembersService extends IService<ShiroGroupMember> {

    void saveOrUpdateGroupMemberInfo(GroupMessageEvent groupEvent);

    void saveOrUpdateGroupMemberInfo(GroupAdminNoticeEvent groupEvent);

    void saveOrUpdateGroupMemberInfo(GroupCardChangeNoticeEvent event);
    ShiroGroupMember getGroupMemberInfo(Long groupId, Long userId);
    void deleteGroupMemberInfo(Long groupId, Long userId);
}
