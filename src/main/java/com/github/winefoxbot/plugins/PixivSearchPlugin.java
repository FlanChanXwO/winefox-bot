package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchParams;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchResult;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.pixiv.PixivSearchService;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import static com.github.winefoxbot.config.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.config.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:29
 */
@Shiro
@Component
@RequiredArgsConstructor
@Slf4j
public class PixivSearchPlugin {
    private final PixivSearchService pixivSearchService;

    @PluginFunction(
            group = "Pixiv", name = "Pixiv搜索",
            permission = Permission.USER,
            description = "在Pixiv上搜索插画作品。命令格式：pixiv搜索 <标签1> <标签2> ... [-p<页码>] [-r]。其中 -p 用于指定页码，-r 用于开启R18搜索。",
            commands = {"pixiv搜索"}, hidden = false)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(?:p|P)(?:ixiv|站)搜索\\s+(.+?)(?=\\s+-|$)\\s*(.*)" + COMMAND_SUFFIX_REGEX)
    public void handlePixivSearch(Bot bot, AnyMessageEvent event, Matcher matcher) {
        PixivSearchParams params = new PixivSearchParams();
        params.setPageNo(1); // 默认从第一页开始
        params.setR18(false); // 默认不开启R18

        // 1. 解析关键词 (Group 1)
        String keywords = matcher.group(1).trim();
        if (keywords.isEmpty()) {
            bot.sendMsg(event, "请输入至少一个搜索标签！", false);
            return;
        }
        List<String> tags = new ArrayList<>(Arrays.asList(keywords.split("\\s+")));
        params.setTags(tags);

        // 2. 解析参数 (Group 2)
        String arguments = matcher.group(2).trim();

        // 如果参数字符串不为空，则进行处理
        if (!arguments.isEmpty()) {
            // 按空格分割所有参数，得到一个参数数组，例如 ["-r", "-p2"]
            String[] args = arguments.split("\\s+");

            // 遍历参数数组，逐个匹配
            for (String arg : args) {
                // 匹配 -r 参数
                if ("-r".equalsIgnoreCase(arg)) {
                    params.setR18(true);
                    continue; // 处理完后继续下一个参数
                }

                // 匹配 -p<页码> 参数
                if (arg.toLowerCase().startsWith("-p")) {
                    // 截取 "-p" 后面的数字部分
                    String pageStr = arg.substring(2);
                    if (!pageStr.isEmpty()) {
                        try {
                            int pageNo = Integer.parseInt(pageStr);
                            // 进行合理的页码范围检查
                            if (pageNo > 0) {
                                params.setPageNo(pageNo);
                            } else {
                                // 如果用户输入-p0或负数，可以提醒或忽略
                                bot.sendMsg(event, "页码必须是大于0的整数哦。", false);
                                return; // 中断执行
                            }
                        } catch (NumberFormatException e) {
                            // 如果-p后面不是数字，例如 "pixiv搜索 a -pabc"
                            log.warn("无效的页码参数: {}", arg);
                            bot.sendMsg(event, "页码参数格式不正确，应为 -p<数字>，例如 -p2。", false);
                            return; // 中断执行
                        }
                    }
                    // 如果是"-p"但后面没数字，可以忽略或给出提示，这里选择忽略
                }
            }
        }

        log.info("开始Pixiv搜索，关键词: {}, 参数: pageNo={}, isR18={}", params.getTags(), params.getPageNo(), params.isR18());

        try {
            // 3. 调用服务层执行搜索
            PixivSearchResult result = pixivSearchService.search(params);
            // 4. 构建并发送回复消息 (增加对结果非空的判断)
            if (result != null && result.getScreenshot() != null && result.getTotalArtworks() > 0) {
                MsgUtils msg = MsgUtils.builder()
                        .text(String.format("为你找到了关于 [%s] 的以下结果：\n", String.join(", ", tags)))
                        .text(String.format("共 %d 个作品，当前在第 %d/%d 页。\n",
                                result.getTotalArtworks(), result.getCurrentPage(), result.getTotalPages()))
                        .img(result.getScreenshot()); // 发送截图

                bot.sendMsg(event, msg.build(), false);
            } else {
                // 对没有找到结果的情况进行更友好的提示
                String noResultMessage = String.format("抱歉，没有找到关于 [%s] 的结果呢。", String.join(" ", tags));
                if (params.isR18()) {
                    noResultMessage += " (已在R18分类下搜索)";
                }
                if(params.getPageNo() > 1){
                    noResultMessage += String.format(" (在第%d页)", params.getPageNo());
                }
                bot.sendMsg(event, noResultMessage, false);
            }
        } catch (Exception e) {
            log.error("Pixiv搜索时发生异常", e);
            bot.sendMsg(event, "搜索过程中发生内部错误，请联系管理员。", false);
        }
    }

}