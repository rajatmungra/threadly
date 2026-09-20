package com.threadly.post;

import com.threadly.post.dto.response.VoteResponse;
import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostType;
import com.threadly.post.entity.PostVote;
import com.threadly.post.repository.PostRepository;
import com.threadly.post.repository.PostVoteRepository;
import com.threadly.post.service.PostVoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.threadly.post.cache.PostFeedCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostVoteIntegrationTest {

    @Autowired
    private PostVoteService postVoteService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostVoteRepository postVoteRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PostFeedCache postFeedCache;

    private Post testPost;

    @BeforeEach
    void setUp() {
        postVoteRepository.deleteAll();
        postRepository.deleteAll();

        Post post = new Post();
        post.setCommunityId(UUID.randomUUID());
        post.setAuthorId(UUID.randomUUID());
        post.setTitle("Vote Integration Test Post");
        post.setType(PostType.TEXT);
        post.setContent("Some test content");
        post.setScore(0);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        testPost = postRepository.save(post);
    }

    @AfterEach
    void tearDown() {
        postVoteRepository.deleteAll();
        postRepository.deleteAll();
    }

    @Test
    void shouldPersistVoteRowAndIncrementStoredPostScore() {
        UUID userId = UUID.randomUUID();

        VoteResponse response = postVoteService.setVote(testPost.getId(), userId, 1);

        assertThat(response.score()).isEqualTo(1);
        assertThat(response.value()).isEqualTo(1);

        // Verify vote row in database
        Optional<PostVote> voteOpt = postVoteRepository.findByPostIdAndUserId(testPost.getId(), userId);
        assertThat(voteOpt).isPresent();
        PostVote vote = voteOpt.get();
        assertThat(vote.getValue()).isEqualTo((short) 1);
        assertThat(vote.getCreatedAt()).isNotNull();
        assertThat(vote.getUpdatedAt()).isNotNull();

        // Verify post score in database
        Post updatedPost = postRepository.findById(testPost.getId()).orElseThrow();
        assertThat(updatedPost.getScore()).isEqualTo(1);
    }

    @Test
    void shouldHandleRepeatedVotesWithoutDuplicatingScoreChanges() {
        UUID userId = UUID.randomUUID();

        postVoteService.setVote(testPost.getId(), userId, 1);
        VoteResponse secondResponse = postVoteService.setVote(testPost.getId(), userId, 1);

        assertThat(secondResponse.score()).isEqualTo(1);

        Post updatedPost = postRepository.findById(testPost.getId()).orElseThrow();
        assertThat(updatedPost.getScore()).isEqualTo(1);
        assertThat(postVoteRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldSwitchVoteCorrectlyAndAdjustScoreByTwo() {
        UUID userId = UUID.randomUUID();

        postVoteService.setVote(testPost.getId(), userId, 1);
        Post postAfterUpvote = postRepository.findById(testPost.getId()).orElseThrow();
        assertThat(postAfterUpvote.getScore()).isEqualTo(1);

        VoteResponse downvoteResponse = postVoteService.setVote(testPost.getId(), userId, -1);
        assertThat(downvoteResponse.score()).isEqualTo(-1);

        Post postAfterDownvote = postRepository.findById(testPost.getId()).orElseThrow();
        assertThat(postAfterDownvote.getScore()).isEqualTo(-1);

        PostVote vote = postVoteRepository.findByPostIdAndUserId(testPost.getId(), userId).orElseThrow();
        assertThat(vote.getValue()).isEqualTo((short) -1);
    }

    @Test
    void shouldRemoveVoteCorrectlyAndAdjustPostScore() {
        UUID userId = UUID.randomUUID();

        postVoteService.setVote(testPost.getId(), userId, 1);
        assertThat(postRepository.findById(testPost.getId()).orElseThrow().getScore()).isEqualTo(1);

        postVoteService.removeVote(testPost.getId(), userId);

        assertThat(postRepository.findById(testPost.getId()).orElseThrow().getScore()).isEqualTo(0);
        assertThat(postVoteRepository.findByPostIdAndUserId(testPost.getId(), userId)).isEmpty();

        // Idempotent removal
        postVoteService.removeVote(testPost.getId(), userId);
        assertThat(postRepository.findById(testPost.getId()).orElseThrow().getScore()).isEqualTo(0);
    }

    @Test
    void shouldHandleConcurrentVotesOnSamePostCorrectly() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> errorUser1 = new AtomicReference<>();
        AtomicReference<Throwable> errorUser2 = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                postVoteService.setVote(testPost.getId(), user1, 1);
            } catch (Throwable t) {
                errorUser1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                postVoteService.setVote(testPost.getId(), user2, 1);
            } catch (Throwable t) {
                errorUser2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Release both threads simultaneously
        startLatch.countDown();

        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errorUser1.get()).isNull();
        assertThat(errorUser2.get()).isNull();

        // Verify both vote rows are persisted
        assertThat(postVoteRepository.findByPostIdAndUserId(testPost.getId(), user1)).isPresent();
        assertThat(postVoteRepository.findByPostIdAndUserId(testPost.getId(), user2)).isPresent();
        assertThat(postVoteRepository.count()).isEqualTo(2L);

        // Verify final score reflects both votes (0 + 1 + 1 = 2)
        Post finalPost = postRepository.findById(testPost.getId()).orElseThrow();
        assertThat(finalPost.getScore()).isEqualTo(2);
    }

    @Test
    void shouldInvalidateFeedCachesOnVoteMutations() {
        UUID communityId = testPost.getCommunityId();
        UUID userId = UUID.randomUUID();

        String newKey = postFeedCache.buildKey(communityId, "new");
        String topKey = postFeedCache.buildKey(communityId, "top");

        // 1. Invalidate on new vote
        redisTemplate.opsForValue().set(newKey, "dummy-new");
        redisTemplate.opsForValue().set(topKey, "dummy-top");
        assertThat(redisTemplate.hasKey(newKey)).isTrue();
        assertThat(redisTemplate.hasKey(topKey)).isTrue();

        postVoteService.setVote(testPost.getId(), userId, 1);
        assertThat(redisTemplate.hasKey(newKey)).isFalse();
        assertThat(redisTemplate.hasKey(topKey)).isFalse();

        // 2. Invalidate on changed vote
        redisTemplate.opsForValue().set(newKey, "dummy-new-2");
        redisTemplate.opsForValue().set(topKey, "dummy-top-2");
        postVoteService.setVote(testPost.getId(), userId, -1);
        assertThat(redisTemplate.hasKey(newKey)).isFalse();
        assertThat(redisTemplate.hasKey(topKey)).isFalse();

        // 3. Invalidate on removed vote
        redisTemplate.opsForValue().set(newKey, "dummy-new-3");
        redisTemplate.opsForValue().set(topKey, "dummy-top-3");
        postVoteService.removeVote(testPost.getId(), userId);
        assertThat(redisTemplate.hasKey(newKey)).isFalse();
        assertThat(redisTemplate.hasKey(topKey)).isFalse();
    }
}
