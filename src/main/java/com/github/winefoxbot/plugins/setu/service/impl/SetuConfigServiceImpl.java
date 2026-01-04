package com.github.winefoxbot.plugins.setu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.plugins.setu.mapper.SetuConfigMapper;
import com.github.winefoxbot.core.model.enums.SessionType;
import com.github.winefoxbot.plugins.setu.model.entity.SetuConfig;
import com.github.winefoxbot.plugins.setu.service.SetuConfigService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
* @author FlanChan
* @description 针对表【setu_config】的数据库操作Service实现
* @createDate 2025-12-28 17:04:06
*/
@Service
public class SetuConfigServiceImpl extends ServiceImpl<SetuConfigMapper, SetuConfig>
    implements SetuConfigService {

    @Override
    @Cacheable(cacheNames = "setuConfig", key = "'session:' + #sessionId + ':' + #sessionType.name()")
    public SetuConfig getOrCreateSetuConfig(Long sessionId, SessionType sessionType) {
        LambdaQueryWrapper<SetuConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetuConfig::getSessionId, sessionId);
        queryWrapper.eq(SetuConfig::getSessionType, sessionType);
        SetuConfig config = this.getOne(queryWrapper);

        if (config == null) {
            config = new SetuConfig();
            config.setSessionId(sessionId);
            config.setSessionType(sessionType);
            config.setR18Enabled(false);
            config.setAutoRevoke(true);
            config.setMaxRequestInSession(1);
            this.save(config);
        }
        return config;
    }

    @Override
    @CacheEvict(cacheNames = "setuConfig", key = "'session:' + #config.sessionId + ':' + #config.sessionType.value")
    public boolean toggleR18Setting(SetuConfig config) {
        config.setR18Enabled(!config.getR18Enabled());
        return this.updateById(config);
    }

    @Override
    @CacheEvict(cacheNames = "setuConfig", key = "'session:' + #config.sessionId + ':' + #config.sessionType.value")
    public boolean toggleAutoRevokeSetting(SetuConfig config) {
        config.setAutoRevoke(!config.getAutoRevoke());
        return this.updateById(config);
    }

    @Override
    @CacheEvict(cacheNames = "setuConfig", key = "'session:' + #config.sessionId + ':' + #config.sessionType.value")
    public boolean updateMaxRequests(SetuConfig config, int newMaxRequests) {
        config.setMaxRequestInSession(newMaxRequests);
        return this.updateById(config);
    }
}




