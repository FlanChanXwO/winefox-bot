package com.github.winefoxbot.core.service.connectionlogs;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.core.model.entity.WinefoxBotConnectionLogs;
import com.github.winefoxbot.core.model.enums.ConnectionEventType;

/**
* @author FlanChan
* @description 针对表【winefox_bot_connection_logs】的数据库操作Service
* @createDate 2026-01-18 18:34:01
*/
public interface WinefoxBotConnectionLogsService extends IService<WinefoxBotConnectionLogs> {

    boolean saveLog(Long botId, ConnectionEventType eventType);
}
