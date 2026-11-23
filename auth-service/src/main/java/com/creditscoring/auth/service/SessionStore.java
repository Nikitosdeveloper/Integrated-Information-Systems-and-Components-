package com.creditscoring.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Активные сессии пользователей в Redis.
 * session:{sessionId} -> userId|role, с ограниченным временем жизни (TTL, продлевается при активности);
 * user-sessions:{userId} -> множество сессий пользователя (для принудительного завершения).
 */
@Component
public class SessionStore {
    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SessionStore(StringRedisTemplate redis, @Value("${auth.session-ttl-minutes}") long ttlMinutes) {
        this.redis = redis;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public void create(String sessionId, String userId, String role) {
        redis.opsForValue().set(key(sessionId), userId + "|" + role, ttl);
        redis.opsForSet().add(userKey(userId), sessionId);
        redis.expire(userKey(userId), ttl.multipliedBy(2));
        log.info("SESSION создана sid={} user={} ttl={}min", sessionId, userId, ttl.toMinutes());
    }

    /** Проверяет, что сессия существует (не истекла), и продлевает её время жизни. */
    public boolean touch(String sessionId) {
        Boolean alive = redis.expire(key(sessionId), ttl);
        return Boolean.TRUE.equals(alive);
    }

    public void delete(String sessionId, String userId) {
        redis.delete(key(sessionId));
        if (userId != null) {
            redis.opsForSet().remove(userKey(userId), sessionId);
        }
        log.info("SESSION завершена sid={}", sessionId);
    }

    public void deleteAllForUser(String userId) {
        Set<String> sessions = redis.opsForSet().members(userKey(userId));
        if (sessions != null) {
            sessions.forEach(s -> redis.delete(key(s)));
        }
        redis.delete(userKey(userId));
        log.info("SESSION все сессии пользователя {} завершены", userId);
    }

    public long incrementFailures(String email, Duration window) {
        String k = "login-fail:" + email.toLowerCase();
        Long n = redis.opsForValue().increment(k);
        if (n != null && n == 1) {
            redis.expire(k, window);
        }
        return n == null ? 0 : n;
    }

    public long failures(String email) {
        String v = redis.opsForValue().get("login-fail:" + email.toLowerCase());
        return v == null ? 0 : Long.parseLong(v);
    }

    public void clearFailures(String email) {
        redis.delete("login-fail:" + email.toLowerCase());
    }

    private static String key(String sessionId) {
        return "session:" + sessionId;
    }

    private static String userKey(String userId) {
        return "user-sessions:" + userId;
    }
}
