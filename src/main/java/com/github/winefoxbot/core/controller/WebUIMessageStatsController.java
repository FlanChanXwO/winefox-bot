package com.github.winefoxbot.core.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.winefoxbot.core.model.entity.ShiroMessage;
import com.github.winefoxbot.core.model.vo.webui.resp.MessageStatisticsResponse;
import com.github.winefoxbot.core.service.shiro.ShiroMessagesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消息统计数据控制器
 * 对应 UI：消息总数/今日消息卡片 + 消息接收环形统计图
 * @author FlanChan
 */
@RestController
@RequestMapping("/api/stats/messages")
@RequiredArgsConstructor
public class WebUIMessageStatsController {

    private final ShiroMessagesService messagesService;

    @GetMapping("/overview/{botId}")
    public MessageStatisticsResponse getMessageOverview(@PathVariable Long botId) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        
        long total = messagesService.count(new LambdaQueryWrapper<>(ShiroMessage.class).eq(ShiroMessage::getSelfId,botId));
        long today = countSince(todayStart,botId);
        long oneDay = countSince(now.minusDays(1),botId);
        long oneWeek = countSince(now.minusWeeks(1),botId);
        long oneMonth = countSince(now.minusMonths(1),botId);
        long oneYear = countSince(now.minusYears(1),botId);

        return new MessageStatisticsResponse(
                total,
                today,
                oneDay,
                oneWeek,
                oneMonth,
                oneYear
        );
    }

    /**
     * 统计指定时间之后的消息数量
     */
    private long countSince(LocalDateTime since, Long botId) {
        return messagesService.count(new LambdaQueryWrapper<ShiroMessage>()
                        .eq(ShiroMessage::getSelfId,botId)
                .ge(ShiroMessage::getTime, since));
    }
}
