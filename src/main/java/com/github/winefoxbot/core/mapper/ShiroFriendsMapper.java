package com.github.winefoxbot.core.mapper;

import com.github.winefoxbot.core.model.entity.ShiroFriends;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_friends(存储每个Bot的好友关系)】的数据库操作Mapper
* @createDate 2026-01-07 18:23:26
* @Entity com.github.winefoxbot.core.model.entity.ShiroFriends
*/
public interface ShiroFriendsMapper extends BaseMapper<ShiroFriends> {
    /**
     * 批量插入或更新（MySQL 原生 Upsert）
     * 性能最佳，仅需一次数据库交互
     *
     * @param list 数据列表
     * @return 影响行数
     */
    int insertOrUpdateBatch(@Param("list") List<ShiroFriends> list);
}




