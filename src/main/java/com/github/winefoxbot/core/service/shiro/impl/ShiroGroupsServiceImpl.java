package com.github.winefoxbot.core.service.shiro.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.mapper.ShiroGroupsMapper;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import com.github.winefoxbot.core.service.shiro.ShiroGroupsService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_groups】的数据库操作Service实现
* @createDate 2025-12-20 07:46:49
*/
@Service
public class ShiroGroupsServiceImpl extends ServiceImpl<ShiroGroupsMapper, ShiroGroup>
    implements ShiroGroupsService{


    @Override
    public void deleteGroupInfo(Long groupId, Long selfId) {
        LambdaQueryWrapper<ShiroGroup> queryWrapper = this.lambdaQuery().getWrapper()
                .eq(ShiroGroup::getGroupId, groupId)
                .eq(ShiroGroup::getSelfId,selfId);
        this.remove(queryWrapper);
    }

    @Override
    public int saveOrUpdateBatchGroups(List<ShiroGroup> shiroGroupList) {
        ShiroGroupsMapper baseMapper = this.getBaseMapper();
        return baseMapper.insertOrUpdateBatch(shiroGroupList);
    }
}




