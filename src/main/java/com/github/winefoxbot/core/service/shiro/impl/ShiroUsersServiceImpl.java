package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroUsersMapper;
import com.github.winefoxbot.core.model.entity.ShiroUser;
import com.github.winefoxbot.core.service.shiro.ShiroUsersService;
import org.springframework.stereotype.Service;

/**
* @author FlanChan
* @description 针对表【shiro_users】的数据库操作Service实现
* @createDate 2025-12-20 07:46:49
*/
@Service
public class ShiroUsersServiceImpl extends ServiceImpl<ShiroUsersMapper, ShiroUser>
    implements ShiroUsersService{

}




