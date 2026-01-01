package com.github.winefoxbot.job;

import com.github.winefoxbot.config.WineFoxBotConfig;
import com.github.winefoxbot.service.chat.DeepSeekService;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

//@Component
@RequiredArgsConstructor
public class WeeklyReminderTask {

    private final WineFoxBotConfig botConfig;
    private final DeepSeekService deepSeekService;
    private final BotContainer botContainer;


    // 每天 17:00 触发检查
    @Scheduled(cron = "0 0 17 * * ?")
//    @Scheduled(cron = "0 34 1 * * ?")
    public void sendWeeklyReminder() {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        // 周一、周二、周四触发
        List<DayOfWeek> reminderDays = Arrays.asList(
                DayOfWeek.MONDAY
        );

        if (!reminderDays.contains(dayOfWeek)) {
            return; // 今天不是提醒日
        }

        Bot bot = botContainer.robots.values().stream().findFirst().get();

        // 构造系统消息
        String systemMessage = "请生成一条群消息提醒大家去抓魔灵，保持酒狐可爱幽默风格";

//        // 调用 AI 生成回复
//        String aiReply = deepSeekService.groupChat(new SystemMessage(botConfig.getSystemPrompt()),new UserMessage("{\"sender\":\"system\",\"uid\":\"0\",\"nickname\":\"系统\",\"message\":\"" + systemMessage + "\"}"));
//        // 构建群消息，@全体成员
//                Bot bot = botContainer.robots.get(2508804038L);
//        if (bot != null) {
//            String replyMsg = MsgUtils.builder()
//                    .atAll() // @全体
//                    .text(" " + aiReply)
//                    .build();
//            bot.sendGroupMsg(1027839334,replyMsg,false);
//        }
    }
}
