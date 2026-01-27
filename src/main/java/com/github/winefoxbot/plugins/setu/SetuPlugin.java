package com.github.winefoxbot.plugins.setu;

import cn.hutool.core.convert.NumberChineseFormatter;
import cn.hutool.core.util.NumberUtil;
import com.github.winefoxbot.core.annotation.plugin.Plugin;
import com.github.winefoxbot.core.annotation.plugin.PluginFunction;
import com.github.winefoxbot.core.model.enums.common.Permission;
import com.github.winefoxbot.core.service.common.SmartTagService;
import com.github.winefoxbot.plugins.setu.config.SetuPluginConfig;
import com.github.winefoxbot.plugins.setu.service.SetuService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.regex.Matcher;

@Plugin(
        name = "瑟瑟功能",
        permission = Permission.USER,
        iconPath = "icon/娱乐功能.png",
        description = "提供随机福利图片获取功能，支持标签和数量限制。",
        order = 7,
        config = SetuPluginConfig.class
)
@Slf4j
@RequiredArgsConstructor
public class SetuPlugin {

    private final SetuService setuService;
    private final SmartTagService tagService;

    private final static int MAX_SETU_COUNT = 10;

    @Async
    @PluginFunction(
            name = "随机福利图片获取",
            description = "使用命令获取随机福利图片，可附加标签和数量限制（默认为1个，最大10个），支持智能匹配你的提示词里的标签，如：来份碧蓝档案福利图，来份白丝萝莉色图",
            commands = {"来份色图", "来张色图", "来份[标签]瑟图", "来个[标签]福利图", "来份[标签]涩图", "来点[标签]色图", "来点[标签]瑟图", "来三份色图", "来点[标签]涩图", "来点[标签]福利图", "来10个[标签]色图","来5份色图" }
    )
    @Order(10)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/?(来\\s*(.*)(份|个|张|点))(\\S*?)(福利|色|瑟|涩|塞|)图$")
    public void getRandomPicture(Bot bot, AnyMessageEvent event, Matcher matcher) {
        String numStr = matcher.group(2);

        int num = parseCount(numStr);

        if (num < 1 || num > MAX_SETU_COUNT) {
            String msg = (num == -1)
                    ? "数量解析失败，请使用数字或中文数字表示正确的数量哦~，图片数量必须在1-%s之间".formatted(MAX_SETU_COUNT)
                    : (num > MAX_SETU_COUNT ? "一次最多只能获取%s张哦~".formatted(MAX_SETU_COUNT) : "图片数量必须在1-%s之间哦~".formatted(MAX_SETU_COUNT));

            bot.sendMsg(event, msg, false);
            return;
        }

        String tag = matcher.group(4); // 获取标签
        // 调用Service处理业务逻辑
        List<String> searchTags = tagService.getSearchTags(tag);
        setuService.handleSetuRequest(num, searchTags);
    }

    /**
     * 解析数量字符串
     * @return 返回解析后的数字，如果为空返回1，解析失败返回-1
     */
    private int parseCount(String text) {
        if (text == null || text.isBlank()) {
            return 1;
        }

        // 尝试解析阿拉伯数字
        int n = NumberUtil.parseInt(text, -1);

        // 如果解析失败，尝试解析中文数字
        if (n == -1) {
            try {
                // 使用 Hutool 或类似的库
                n = NumberChineseFormatter.chineseToNumber(text);
            } catch (Exception _) {
                return -1;
            }
        }

        return n;
    }

}
