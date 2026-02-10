package com.github.winefoxbot.plugins.linkresolver.constant;

import java.time.Duration;

public interface LinkResolverConstants {
    /**
     * The cache duration for resolved resources like images and videos.
     */
    Duration RESOURCE_CACHE_DURATION = Duration.ofHours(1);
}