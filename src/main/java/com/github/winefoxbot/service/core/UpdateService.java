package com.github.winefoxbot.service.core;

import com.github.winefoxbot.service.core.impl.UpdateServiceImpl;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-09-20:08
 */
public interface UpdateService {
    void updateJar(String downloadUrl, String currentJarPath) throws Exception;

    UpdateServiceImpl.ReleaseInfo getLatestRelease() throws Exception;
}
