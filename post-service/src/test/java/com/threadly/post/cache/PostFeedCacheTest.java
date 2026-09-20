package com.threadly.post.cache;

import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import com.threadly.post.entity.PostType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostFeedCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private PostFeedCache postFeedCache;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        postFeedCache = new PostFeedCache(redisTemplate, objectMapper, 60);
    }

    @Test
    void shouldBuildExpectedCacheKeys() {
        UUID communityId = UUID.randomUUID();
        assertThat(postFeedCache.buildKey(communityId, "new"))
                .isEqualTo("posts:" + communityId + ":new:0:20");
        assertThat(postFeedCache.buildKey(communityId, "top"))
                .isEqualTo("posts:" + communityId + ":top:0:20");
    }

    @Test
    void shouldReturnCachedPagedResponseOnHit() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        PostResponse postResponse = new PostResponse(
                postId, communityId, UUID.randomUUID(), "Title", "Content", null,
                PostType.TEXT, 10, 0, Instant.now(), Instant.now()
        );
        PagedResponse<PostResponse> pagedResponse = new PagedResponse<>(
                List.of(postResponse), 0, 20, 1, 1, true
        );
        String json = objectMapper.writeValueAsString(pagedResponse);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("posts:" + communityId + ":new:0:20")).thenReturn(json);

        Optional<PagedResponse<PostResponse>> result = postFeedCache.get(communityId, "new");

        assertThat(result).isPresent();
        assertThat(result.get().content()).hasSize(1);
        assertThat(result.get().content().getFirst().id()).isEqualTo(postId);
        assertThat(result.get().content().getFirst().score()).isEqualTo(10);
    }

    @Test
    void shouldReturnEmptyOnCacheMiss() {
        UUID communityId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("posts:" + communityId + ":top:0:20")).thenReturn(null);

        Optional<PagedResponse<PostResponse>> result = postFeedCache.get(communityId, "top");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenRedisThrowsDataAccessExceptionOnGet() {
        UUID communityId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatCode(() -> {
            Optional<PagedResponse<PostResponse>> result = postFeedCache.get(communityId, "new");
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldReturnEmptyWhenCachedJsonIsCorrupt() {
        UUID communityId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("posts:" + communityId + ":new:0:20")).thenReturn("{invalid-json");

        Optional<PagedResponse<PostResponse>> result = postFeedCache.get(communityId, "new");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPutPagedResponseWithTtl() {
        UUID communityId = UUID.randomUUID();
        PostResponse postResponse = new PostResponse(
                UUID.randomUUID(), communityId, UUID.randomUUID(), "Title", "Content", null,
                PostType.TEXT, 5, 0, Instant.now(), Instant.now()
        );
        PagedResponse<PostResponse> pagedResponse = new PagedResponse<>(
                List.of(postResponse), 0, 20, 1, 1, true
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        postFeedCache.put(communityId, "new", pagedResponse);

        verify(valueOperations).set(
                eq("posts:" + communityId + ":new:0:20"),
                anyString(),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void shouldNotThrowWhenRedisFailsOnPut() {
        UUID communityId = UUID.randomUUID();
        PagedResponse<PostResponse> pagedResponse = new PagedResponse<>(
                List.of(), 0, 20, 0, 0, true
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> postFeedCache.put(communityId, "top", pagedResponse))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldEvictBothNewAndTopKeys() {
        UUID communityId = UUID.randomUUID();

        postFeedCache.evict(communityId);

        verify(redisTemplate).delete(List.of(
                "posts:" + communityId + ":new:0:20",
                "posts:" + communityId + ":top:0:20"
        ));
    }

    @Test
    void shouldNotThrowWhenRedisFailsOnEvict() {
        UUID communityId = UUID.randomUUID();
        when(redisTemplate.delete(any(List.class)))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        assertThatCode(() -> postFeedCache.evict(communityId))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldEvictImmediatelyWhenNoTransactionSynchronizationActive() {
        UUID communityId = UUID.randomUUID();

        postFeedCache.evictAfterCommit(communityId);

        verify(redisTemplate).delete(List.of(
                "posts:" + communityId + ":new:0:20",
                "posts:" + communityId + ":top:0:20"
        ));
    }

    @Test
    void shouldRegisterSynchronizationAndEvictAfterCommitWhenTransactionActive() {
        UUID communityId = UUID.randomUUID();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            postFeedCache.evictAfterCommit(communityId);

            // Should not have evicted yet
            verify(redisTemplate, never()).delete(any(List.class));

            // Simulate commit
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }

            verify(redisTemplate).delete(List.of(
                    "posts:" + communityId + ":new:0:20",
                    "posts:" + communityId + ":top:0:20"
            ));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
