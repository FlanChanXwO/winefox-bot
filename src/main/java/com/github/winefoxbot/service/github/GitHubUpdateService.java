package com.github.winefoxbot.service.github;

import com.github.winefoxbot.model.dto.core.RestartInfo;
import com.github.winefoxbot.model.dto.github.GitHubRelease;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.Data;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-25-22:23
 */
public interface GitHubUpdateService {
    VersionInfo getCurrentVersionInfo();

    GitHubRelease fetchLatestRelease() throws Exception;

    GitHubRelease.Asset findJarAsset(GitHubRelease.Asset[] assets);

    void performUpdate(Bot bot, AnyMessageEvent event) throws Exception;

    void restartApplication();

    void saveRestartInfo(RestartInfo restartInfo);

    @Data
    class VersionInfo {
        public long releaseId = -1;
        public long assetId = -1;

        @Override
        public String toString() {
            return "(Release ID: %d | Asset Id：%d)".formatted(releaseId,assetId);
        }
    }
}
