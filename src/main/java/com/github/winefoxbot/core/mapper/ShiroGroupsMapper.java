package com.github.winefoxbot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.core.model.entity.ShiroGroup;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author FlanChan
* @description 针对表【shiro_groups】的数据库操作Mapper
* @createDate 2025-12-20 07:46:49
* @Entity generator.domain.ShiroGroups
*/
public interface ShiroGroupsMapper extends BaseMapper<ShiroGroup> {
    /**
     * 批量插入或更新（MySQL 原生 Upsert）
     * 性能最佳，仅需一次数据库交互
     *
     * @param list 数据列表
     * @return 影响行数
     */
    int insertOrUpdateBatch(@Param("list") List<ShiroGroup> list);
}




