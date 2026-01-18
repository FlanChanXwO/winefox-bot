package com.github.winefoxbot.core.service.connectionlogs.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.model.entity.WinefoxBotConnectionLogs;
import com.github.winefoxbot.core.model.enums.LogEventType;
import com.github.winefoxbot.core.service.connectionlogs.WinefoxBotConnectionLogsService;
import com.github.winefoxbot.core.mapper.WinefoxBotConnectionLogsMapper;
import org.springframework.stereotype.Service;

/**
* @author FlanChan
* @description 针对表【winefox_bot_connection_logs】的数据库操作Service实现
* @createDate 2026-01-18 18:34:01
*/
@Service
public class WinefoxBotConnectionLogsServiceImpl extends ServiceImpl<WinefoxBotConnectionLogsMapper, WinefoxBotConnectionLogs>
    implements WinefoxBotConnectionLogsService{

    @Override
    public boolean saveLog(Long botId, LogEventType eventType) {
        WinefoxBotConnectionLogs log = new WinefoxBotConnectionLogs();
        log.setBotId(botId);
        log.setEventType(eventType);
        return this.save(log);
    }
}




