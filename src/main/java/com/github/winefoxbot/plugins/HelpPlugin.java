package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.service.core.HelpImageService;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-16-11:05
 */
@Shiro
@Component
@Slf4j
@RequiredArgsConstructor
public class HelpPlugin {
    private final HelpImageService helpImageService;

    private final String backgroundImagePath = "/opt/software/winefox-bot/help_background.png";

    @PluginFunction(
            group = "核心功能", name = "帮助文档",
            description = "生成并发送帮助图片，展示所有可用功能及其说明。", permission = Permission.USER,
            commands = {"/help", "/支援", "/h", "/酒狐的特殊能力", "/wf帮助"})
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = "^/(支援|help|h|酒狐的特殊能力|wf帮助)(?:\\s+(.+))?$")
    public void helpCommand(Bot bot, AnyMessageEvent event, Matcher matcher) {
        try {
            log.info("正在生成帮助图片...");
            // 0.获取参数（如果有的话）
            String param = matcher.group(2);
            // 1. 调用服务生成 BufferedImage 对象
            // 这里使用 Optional.empty() 表示不提供自定义背景，使用默认的模糊玻璃效果
            BufferedImage bufferedImage;
            try(InputStream bgImageStream = Files.newInputStream(Path.of(backgroundImagePath))) {
                 bufferedImage = param != null ? helpImageService.generateGroupHelpImage(param, Optional.of(bgImageStream))
                        : helpImageService.generateAllHelpImage(Optional.of(bgImageStream));
            } catch (IOException e) {
                log.error("读取自定义背景图片时发生错误，使用默认背景。", e);
                bufferedImage = param != null ? helpImageService.generateGroupHelpImage(param, Optional.empty())
                        : helpImageService.generateAllHelpImage(Optional.empty());
            }
            if (bufferedImage == null) {
                log.warn("请求的帮助分组 '{}' 不存在，无法生成帮助图片。", param);
                bot.sendMsg(event, "抱歉，未找到您请求的帮助分组，请检查分组名称是否正确。", false);
                return;
            }
            // 2. 将 BufferedImage 转换为 byte[]
            byte[] imageBytes;
            // 使用 try-with-resources 确保 ByteArrayOutputStream 被正确关闭
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                // 将图片以 PNG 格式写入到内存输出流
                ImageIO.write(bufferedImage, "png", baos);
                // 从输出流获取字节数组
                imageBytes = baos.toByteArray();
            }

            log.info("帮助图片生成完毕，大小为: {} bytes。准备发送...", imageBytes.length);

            // 3. 将 byte[] 传递给你的 img() 方法
            bot.sendMsg(event, MsgUtils.builder()
                    .img(imageBytes) // 在这里传入字节数组
                    .build(), false);

            log.info("帮助图片已发送。");

        } catch (IOException e) {
            log.error("生成或发送帮助图片时发生IO异常", e);
            bot.sendMsg(event, "抱歉，生成帮助图片时出现了一点小问题，请联系管理员。", false);
        } catch (Exception e) {
            // 捕获其他可能的运行时异常，例如绘图时发生的错误
            log.error("生成帮助图片时发生未知错误", e);
            bot.sendMsg(event, "抱歉，生成帮助图片时发生未知错误，请稍后再试。", false);
        }
    }

}