package com.github.winefoxbot.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.winefoxbot.core.model.entity.WinefoxBotPluginConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WinefoxBotAppConfigMapper extends BaseMapper<WinefoxBotPluginConfig> {
}