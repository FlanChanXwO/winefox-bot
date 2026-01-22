package com.github.winefoxbot.core.config.inner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CacheConfig {

    /**
     * 配置RedisCacheManager
     * 设置默认缓存配置和各个缓存的特定配置
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // 默认过期时间10分钟
                .disableCachingNullValues()  // 不缓存空值
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.string()))  // key序列化
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(RedisSerializer.json()));  // value序列化
        // 各个缓存的特定配置
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
//        // 用户缓存:1小时过期
//        cacheConfigs.put("users", RedisCacheConfiguration.defaultCacheConfig()
//                .entryTtl(Duration.ofHours(1))
//                .prefixCacheNameWith("users:"));
//        // 商品缓存:30分钟过期
//        cacheConfigs.put("products", RedisCacheConfiguration.defaultCacheConfig()
//                .entryTtl(Duration.ofMinutes(30))
//                .prefixCacheNameWith("products:"));
//        // 配置缓存:不过期
//        cacheConfigs.put("config", RedisCacheConfiguration.defaultCacheConfig()
//                .prefixCacheNameWith("config:")
//                .disableKeyPrefix());  // 不使用前缀
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()  // 支持事务
                .build();
    }


}


