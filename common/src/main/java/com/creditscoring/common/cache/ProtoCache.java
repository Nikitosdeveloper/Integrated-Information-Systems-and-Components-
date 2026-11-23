package com.creditscoring.common.cache;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Cache-aside кеш в Redis для protobuf-сообщений (хранятся как JSON).
 * Недоступность Redis не нарушает работу: при ошибке данные читаются из источника.
 */
public class ProtoCache {
    private static final Logger log = LoggerFactory.getLogger("cache");
    private static final String PREFIX = "cache:";

    private final StringRedisTemplate redis;

    public ProtoCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @SuppressWarnings("unchecked")
    public <T extends Message> T getOrLoad(String key, T prototype, Duration ttl, Supplier<T> loader) {
        String fullKey = PREFIX + key;
        try {
            String json = redis.opsForValue().get(fullKey);
            if (json != null) {
                Message.Builder builder = prototype.newBuilderForType();
                JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
                log.info("CACHE HIT  {}", fullKey);
                return (T) builder.build();
            }
        } catch (Exception e) {
            log.warn("CACHE ERROR (чтение) {}: {} - читаем из источника", fullKey, e.toString());
        }
        log.info("CACHE MISS {}", fullKey);
        T value = loader.get();
        try {
            redis.opsForValue().set(fullKey, JsonFormat.printer().includingDefaultValueFields().print(value), ttl);
        } catch (Exception e) {
            log.warn("CACHE ERROR (запись) {}: {}", fullKey, e.toString());
        }
        return value;
    }

    public void evict(String... keys) {
        for (String key : keys) {
            try {
                redis.delete(PREFIX + key);
                log.info("CACHE EVICT {}{}", PREFIX, key);
            } catch (Exception e) {
                log.warn("CACHE ERROR (удаление) {}{}: {}", PREFIX, key, e.toString());
            }
        }
    }
}
