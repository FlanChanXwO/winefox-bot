package com.github.winefoxbot.plugins.setu.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.enums.SessionType;
import com.github.winefoxbot.plugins.setu.model.entity.SetuConfig;

/**
* @author FlanChan
* @description 针对表【setu_config】的数据库操作Service
* @createDate 2025-12-28 17:04:06
*/
public interface SetuConfigService extends IService<SetuConfig> {

    SetuConfig getOrCreateSetuConfig(Long sessionId, SessionType sessionType);

    boolean toggleR18Setting(SetuConfig config);

    boolean toggleAutoRevokeSetting(SetuConfig config);

    boolean updateMaxRequests(SetuConfig config, int newMaxRequests);
}
