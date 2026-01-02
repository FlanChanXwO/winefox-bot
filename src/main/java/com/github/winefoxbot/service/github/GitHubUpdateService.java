package com.github.winefoxbot.service.github;

import com.github.winefoxbot.model.dto.github.GitHubRelease;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-25-22:23
 */
public interface GitHubUpdateService {
    VersionInfo getCurrentVersionInfo();

    GitHubRelease fetchLatestRelease() throws Exception;

    GitHubRelease.Asset findJarAsset(GitHubRelease.Asset[] assets);

    void performUpdate() throws Exception;

    void restartApplication();

    class VersionInfo {
        public long releaseId = -1;
        public long assetId = -1;
    }
}
