package com.github.winefoxbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.model.entity.AppConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppConfigMapper extends BaseMapper<AppConfig> {
}