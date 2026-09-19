package com.threadly.post.cache;

import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostFeedCache {

    private static final Logger log = LoggerFactory.getLogger(PostFeedCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;
    private final JavaType pagedResponseType;

    @org.springframework.beans.factory.annotation.Autowired
    public PostFeedCache(
            StringRedisTemplate redisTemplate,
            @Value("${cache.feed.ttl-seconds:60}") long ttlSeconds
    ) {
        this(redisTemplate, JsonMapper.builder().build(), ttlSeconds);
    }

    public PostFeedCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : JsonMapper.builder().build();
        this.ttlSeconds = ttlSeconds;
        this.pagedResponseType = this.objectMapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, PostResponse.class);
    }

    public Optional<PagedResponse<PostResponse>> get(UUID communityId, String sort) {
        if (communityId == null || sort == null) {
            return Optional.empty();
        }
        String key = buildKey(communityId, sort);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                PagedResponse<PostResponse> response = objectMapper.readValue(json, pagedResponseType);
                return Optional.of(response);
            }
        } catch (DataAccessException e) {
            log.warn("Redis access failure while reading feed cache for key {}: {}", key, e.getMessage());
        } catch (JacksonException e) {
            log.warn("JSON deserialization failure for feed cache key {}: {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void put(UUID communityId, String sort, PagedResponse<PostResponse> response) {
        if (communityId == null || sort == null || response == null) {
            return;
        }
        String key = buildKey(communityId, sort);
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("Redis access failure while writing feed cache for key {}: {}", key, e.getMessage());
        } catch (JacksonException e) {
            log.warn("JSON serialization failure for feed cache key {}: {}", key, e.getMessage());
        }
    }

    public void evict(UUID communityId) {
        if (communityId == null) {
            return;
        }
        String newKey = buildKey(communityId, "new");
        String topKey = buildKey(communityId, "top");
        try {
            redisTemplate.delete(List.of(newKey, topKey));
        } catch (DataAccessException e) {
            log.warn("Redis access failure while evicting feed cache for community {}: {}", communityId, e.getMessage());
        }
    }

    public void evictAfterCommit(UUID communityId) {
        if (communityId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(communityId);
                }
            });
        } else {
            evict(communityId);
        }
    }

    public String buildKey(UUID communityId, String sort) {
        return "posts:" + communityId + ":" + sort + ":0:20";
    }
}
