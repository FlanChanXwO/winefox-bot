package com.github.winefoxbot.service.shiro.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.mapper.ShiroUsersMapper;
import com.github.winefoxbot.model.entity.ShiroUser;
import com.github.winefoxbot.service.shiro.ShiroUsersService;
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




