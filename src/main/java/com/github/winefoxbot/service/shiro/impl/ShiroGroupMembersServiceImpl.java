package com.github.winefoxbot.service.shiro.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.model.entity.ShiroGroupMember;
import com.github.winefoxbot.service.shiro.ShiroGroupMembersService;
import com.github.winefoxbot.mapper.ShiroGroupMembersMapper;
import org.springframework.stereotype.Service;

/**
* @author FlanChan
* @description 针对表【shiro_group_members】的数据库操作Service实现
* @createDate 2025-12-20 07:46:49
*/
@Service
public class ShiroGroupMembersServiceImpl extends ServiceImpl<ShiroGroupMembersMapper, ShiroGroupMember>
    implements ShiroGroupMembersService{

}




