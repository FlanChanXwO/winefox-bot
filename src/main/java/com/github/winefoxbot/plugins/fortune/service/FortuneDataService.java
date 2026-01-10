package com.github.winefoxbot.plugins.fortune.service;

import com.github.winefoxbot.plugins.fortune.model.entity.FortuneData;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.plugins.fortune.model.vo.FortuneRenderVO;

/**
* @author FlanChan
* @description 针对表【fortune_data(今日运势数据表)】的数据库操作Service
* @createDate 2026-01-10 05:06:35
*/
public interface FortuneDataService extends IService<FortuneData> {

    void processFortune(Bot bot, AnyMessageEvent event);

    FortuneRenderVO getFortuneRenderVO(long userId, String displayName);

    void sendFortuneImage(Bot bot, long userId, Long groupId, MessageType type, FortuneRenderVO vo);

    void refreshFortune(Bot bot, AnyMessageEvent event);

    void refreshAllFortune(Bot bot, AnyMessageEvent event);
}
