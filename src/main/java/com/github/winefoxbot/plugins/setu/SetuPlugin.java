package com.github.winefoxbot.plugins.setu;

import cn.hutool.core.util.NumberUtil;
import com.github.winefoxbot.core.annotation.Limit;
import com.github.winefoxbot.core.annotation.Plugin;
import com.github.winefoxbot.core.annotation.PluginFunction;
import com.github.winefoxbot.core.model.enums.Permission;
import com.github.winefoxbot.core.service.shiro.ShiroSessionStateService;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;

@Plugin(
        name = "娱乐功能",
        permission = Permission.USER,
        iconPath = "icon/娱乐功能.png",
        order = 7
)
@Component
@Shiro
@Slf4j
@RequiredArgsConstructor
public class SetuPlugin {

    private final ShiroSessionStateService shiroSessionStateService;
    private final SetuService setuService;

    @Limit(globalPermits = 20, userPermits = 3 , timeInSeconds = 3)
    @Async
    @PluginFunction(
            name = "随机福利图片获取",
            description = "使用命令获取随机福利图片，可附加标签和数量限制（默认为1个，最大10个），如：来份碧蓝档案福利图",
            commands = {"来份色图", "来张色图", "来份[标签]瑟图", "来个[标签]福利图", "来份[标签]涩图", "来点[标签]色图", "来点[标签]瑟图", "来点[标签]涩图", "来点[标签]福利图", "来10个[标签]色图","来5份色图" }
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^(来\\s*(.*)(份|个|张|点))(\\S*?)(福利|色|瑟|涩|塞|)图$")
    public void getRandomPicture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        // 仅在私聊时进入命令模式，避免影响群聊体验
        if (event.getGroupId() == null) {
            String sessionKey = shiroSessionStateService.getSessionKey(event);
            shiroSessionStateService.enterCommandMode(sessionKey);
        }
        int num = NumberUtil.parseInt(matcher.group(2), 1);
        if (0 >= num || num > 10) {
            bot.sendMsg(event, "一次最多只能获取10张哦~", false);
            return;
        }
        String tag = matcher.group(4); // 获取标签
        // 调用Service处理业务逻辑
        setuService.processSetuRequest(bot, event,num, tag);
    }
}
