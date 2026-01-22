package com.github.winefoxbot.core.service.update;

import com.github.winefoxbot.core.model.dto.update.GitHubRelease;
import com.github.winefoxbot.core.model.dto.RestartInfo;
import com.github.winefoxbot.core.model.dto.update.GithubVersionInfo;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-25-22:23
 */
public interface GitHubUpdateService {
    GithubVersionInfo getCurrentVersionInfo();

    GitHubRelease fetchLatestRelease() throws Exception;

    GitHubRelease.Asset findJarAsset(GitHubRelease.Asset[] assets);

    void performUpdate(Bot bot, AnyMessageEvent event) throws Exception;

    void restartApplication(AnyMessageEvent event);

    void saveRestartInfo(RestartInfo restartInfo);


}
