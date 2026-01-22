package com.github.winefoxbot.core.model.dto.update;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/**
 * @author FlanChan
 */
@Data
public class GithubVersionInfo {
    private long releaseId = -1;
    private String tagName;
    private long assetId = -1;

    private long libAssetId = -1;
    private String libSha256;

    private long resourcesAssetId = -1;
    private String resourcesSha256;

    @Override
    public @NonNull String toString() {
        return "(Tag: %s | Release ID: %d | Asset Id: %d | Lib Asset Id: %d)".formatted(tagName, releaseId, assetId, libAssetId);
    }
}