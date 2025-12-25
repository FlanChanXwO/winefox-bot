package com.github.winefoxbot.plugins;

import com.github.winefoxbot.service.core.UpdateService;
import com.github.winefoxbot.service.core.impl.UpdateServiceImpl;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 更新插件
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-08-21:35
 */
@Shiro
@Component
public class UpdatePlugin {

    private final UpdateService updateService;

    public UpdatePlugin(UpdateService updateService) {
        this.updateService = updateService;
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "^/version$")
    public void checkVersion(Bot bot, AnyMessageEvent event) {
        try {
            UpdateServiceImpl.ReleaseInfo latestRelease = updateService.getLatestRelease();
            String version = latestRelease.version;
            bot.sendMsg(event,"当前版本 ：v1.0.0\n最新版本：" + version + "\n暂无更新功能，敬请期待！",false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public void updateVersion(Bot bot, AnyMessageEvent event) {
        try {
            UpdateServiceImpl.ReleaseInfo latestRelease = updateService.getLatestRelease();
            String version = latestRelease.version;
            String downloadUrl = latestRelease.url;
            if (!"1.0.0".equals(version)) {
                bot.sendMsg(event,"当前已是最新版本：" + latestRelease.version,false);
                return;
            }
            bot.sendMsg(event,"正在更新到最新版本：" + latestRelease.version + "，请稍候...",false);
            String currentJarPath = new File(UpdatePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            updateService.updateJar(downloadUrl, currentJarPath);
        } catch (Exception e) {
            bot.sendMsg(event,"更新失败：" + e.getMessage(),false);
        }
    }
}