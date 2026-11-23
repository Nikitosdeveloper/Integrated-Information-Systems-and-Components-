package com.creditscoring.common.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ProtoCacheConfig {

    @Bean
    public ProtoCache protoCache(StringRedisTemplate redis) {
        return new ProtoCache(redis);
    }
}
