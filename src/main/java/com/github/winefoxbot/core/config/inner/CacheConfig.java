package com.github.winefoxbot.core.config.inner;

import com.github.winefoxbot.core.constants.CacheConstants;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

import static com.github.winefoxbot.core.constants.CacheConstants.CACHE_KEY_PREFIX;

@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        // 全局默认配置
        return RedisCacheConfiguration.defaultCacheConfig()
                // 设置全局默认过期时间为 30 分钟
                .entryTtl(Duration.ofHours(1))
                // 设置 key 的序列化方式 (可选)
                // .serializeKeysWith(...) 
                // 设置 value 的序列化方式为 JSON
                .prefixCacheNameWith(CACHE_KEY_PREFIX) // 设置缓存前缀
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                // 不缓存 null 值
                .disableCachingNullValues();
    }
    
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                // 为 pixivArtworkInfoCache 单独设置 2 小时过期
                .withCacheConfiguration(CacheConstants.PIXIV_ARTWORK_INFO_CACHE,
                        RedisCacheConfiguration.defaultCacheConfig()
                                .prefixCacheNameWith(CACHE_KEY_PREFIX)
                                .entryTtl(Duration.ofHours(2))
                                .disableCachingNullValues()
                                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                );
    }
}
