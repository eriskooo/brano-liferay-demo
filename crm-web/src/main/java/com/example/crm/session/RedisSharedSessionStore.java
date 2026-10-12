package com.example.crm.session;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis implementácia – rovnaké kľúče a TTL ako {@code RedisSessionStore} starej appky (Jedis, Java 8).
 * Kontrakt medzi appkami tvorí len názov hashu, názvy polí a JSON tvar hodnôt.
 */
@Component
public class RedisSharedSessionStore implements SharedSessionStore {

    static final String KEY_PREFIX = "crm:session:";
    static final String FLAG_KEY = "crm:flags:session-read-source";
    static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;

    public RedisSharedSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> getJson(String ssoId, String key) {
        Object value = redis.opsForHash().get(KEY_PREFIX + ssoId, key);
        redis.expire(KEY_PREFIX + ssoId, TTL);
        return Optional.ofNullable((String) value);
    }

    @Override
    public void putJson(String ssoId, String key, String json) {
        redis.opsForHash().put(KEY_PREFIX + ssoId, key, json);
        redis.expire(KEY_PREFIX + ssoId, TTL);
    }

    @Override
    public void remove(String ssoId, String key) {
        redis.opsForHash().delete(KEY_PREFIX + ssoId, key);
    }

    @Override
    public Optional<String> legacyReadSource() {
        return Optional.ofNullable(redis.opsForValue().get(FLAG_KEY));
    }
}
