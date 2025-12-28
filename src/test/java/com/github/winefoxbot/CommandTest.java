package com.github.winefoxbot;

import com.mikuac.shiro.common.utils.JsonObjectWrapper;
import com.mikuac.shiro.constant.ActionParams;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.enums.ActionPathEnum;
import com.mikuac.shiro.handler.ActionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-11:18
 */
@SpringBootTest
public class CommandTest {

    private @Autowired BotContainer botContainer;

    @Test
    void func() {
        Optional<Bot> botOptional = botContainer.robots.values().stream().findFirst();
        if (botOptional.isPresent()) {
            Bot bot = botOptional.get();
            ActionHandler actionHandler1 = bot.getActionHandler();
            actionHandler1.onReceiveActionResp(new JsonObjectWrapper("{\"type\":\"text\",\"data\":{\"text\":\"/echo 1\"}}"));
        }
    }

    public static void main(String[] args) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println(formatter.format(LocalDateTime.now()));
    }
}