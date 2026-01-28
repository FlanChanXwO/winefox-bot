package com.github.winefoxbot.plugins.deerpipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.plugins.deerpipe.model.entity.DeerUserConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeerUserConfigMapper extends BaseMapper<DeerUserConfig> {
}
