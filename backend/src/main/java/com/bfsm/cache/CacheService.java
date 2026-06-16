package com.bfsm.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.function.Supplier;

@Service
public class CacheService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public CacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrLoad(String key, Class<T> clazz, Duration ttl, Supplier<T> loader) {
        try {
            String cache = redisTemplate.opsForValue().get(key);
            if (cache != null) {
                if ("__NULL__".equals(cache)) {
                    return null;
                }
                try {
                    return objectMapper.readValue(cache, clazz);
                } catch (JsonProcessingException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        T loaded = loader.get();
        if (loaded == null) {
            safeSet(key, "__NULL__", Duration.ofMinutes(5));
            return null;
        }
        safeSetJson(key, loaded, ttl);
        return loaded;
    }

    public <T> T getOrLoad(String key, TypeReference<T> typeReference, Duration ttl, Supplier<T> loader) {
        try {
            String cache = redisTemplate.opsForValue().get(key);
            if (cache != null) {
                if ("__NULL__".equals(cache)) {
                    return null;
                }
                try {
                    return objectMapper.readValue(cache, typeReference);
                } catch (JsonProcessingException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        T loaded = loader.get();
        if (loaded == null) {
            safeSet(key, "__NULL__", Duration.ofMinutes(5));
            return null;
        }
        safeSetJson(key, loaded, ttl);
        return loaded;
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }

    private void safeSetJson(String key, Object loaded, Duration ttl) {
        try {
            long plus = random.nextInt(120);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(loaded), ttl.plusSeconds(plus));
        } catch (Exception ignored) {
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
        }
    }
}
