package com.github.winefoxbot.listener;

import com.github.winefoxbot.config.WineFoxBotConfig;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.CoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class BotStatusEventListener extends CoreEvent {


    private final WineFoxBotConfig wineFoxBotConfig;
    @Override
    public void online(Bot bot) {
        // 客户端上线事件
        // 例如上线后发送消息给指定的群或好友
        // 如需获取上线的机器人账号可以调用 bot.getSelfId()
        bot.sendPrivateMsg(wineFoxBotConfig.getMaster(), "我上线啦～", false);
    }

    @Override
    public void offline(long account) {
        // 客户端离线事件
        log.warn("诶～我又离线了");
    }

    @Override
    public boolean session(WebSocketSession session) {
        // 可以通过 session.getHandshakeHeaders().getFirst("x-self-id") 获取上线的机器人账号
        // 例如当服务端为开放服务时，并且只有白名单内的账号才允许连接，此时可以检查账号是否存在于白名内
        // 不存在的话返回 false 即可禁止连接
        return true;
    }

}
