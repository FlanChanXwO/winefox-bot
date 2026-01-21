package com.github.winefoxbot.core.config.inner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

import static com.github.winefoxbot.core.constants.CacheConstants.CACHE_KEY_PREFIX;

@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration(ObjectMapper objectMapper) {
        // 全局默认配置
        return RedisCacheConfiguration.defaultCacheConfig()
                // 设置全局默认过期时间为 1 天
                .entryTtl(Duration.ofDays(1))
                // 设置 key 的序列化方式 (可选)
                .prefixCacheNameWith(CACHE_KEY_PREFIX) // 设置缓存前缀
                // 设置 Key 为 String 序列化 (这是最佳实践，方便在 Redis GUI 中查看)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.string()))
                // 设置 Value 为 JSON 序列化 (包含 @class 类型信息)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json()))
                // 不缓存 null 值
                .disableCachingNullValues();
    }

    @Bean
    public RedisCacheWriter redisCacheWriter(RedisConnectionFactory redisConnectionFactory) {
        return RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);
    }

    @Bean
    public CacheManager cacheManager(RedisCacheWriter redisCacheWriter,
                                     RedisCacheConfiguration redisCacheConfiguration) {
        return new RedisCacheManager(redisCacheWriter,redisCacheConfiguration,true);
    }
}
