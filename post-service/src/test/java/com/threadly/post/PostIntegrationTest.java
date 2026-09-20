package com.threadly.post;

import com.threadly.post.client.CommunityClient;
import com.threadly.post.dto.request.CreatePostRequest;
import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostType;
import com.threadly.post.event.CommentCreatedEvent;
import com.threadly.post.exception.CommunityNotFoundException;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.repository.PostRepository;
import com.threadly.post.repository.ProcessedCommentEventRepository;
import com.threadly.post.service.CommentCountService;
import com.threadly.post.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.threadly.post.cache.PostFeedCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PostIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private PostFeedCache postFeedCache;

    @Autowired
    private CommentCountService commentCountService;

    @Autowired
    private ProcessedCommentEventRepository processedCommentEventRepository;

    @MockitoBean
    private CommunityClient communityClient;

    @BeforeEach
    void setUp() {
        processedCommentEventRepository.deleteAll();
        postRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        processedCommentEventRepository.deleteAll();
        postRepository.deleteAll();
    }

    @Test
    void shouldCreateTextPostInDatabase() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "Integration Test Text Post",
            PostType.TEXT,
            "Detailed text content for integration test.",
            null
        );

        PostResponse response = postService.createPost(request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.communityId()).isEqualTo(communityId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.title()).isEqualTo("Integration Test Text Post");
        assertThat(response.content()).isEqualTo("Detailed text content for integration test.");
        assertThat(response.url()).isNull();
        assertThat(response.type()).isEqualTo(PostType.TEXT);
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.commentCount()).isEqualTo(0);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        verify(communityClient).verifyCommunityExists(communityId, token);

        Optional<Post> found = postRepository.findById(response.id());
        assertThat(found).isPresent();
        Post post = found.get();
        assertThat(post.getTitle()).isEqualTo("Integration Test Text Post");
        assertThat(post.getContent()).isEqualTo("Detailed text content for integration test.");
        assertThat(post.getUrl()).isNull();
        assertThat(post.getType()).isEqualTo(PostType.TEXT);
        assertThat(post.getScore()).isEqualTo(0);
        assertThat(post.getCommentCount()).isEqualTo(0);
    }

    @Test
    void shouldCreateLinkPostInDatabase() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "Integration Test Link Post",
            PostType.LINK,
            null,
            "https://spring.io/projects/spring-boot"
        );

        PostResponse response = postService.createPost(request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.communityId()).isEqualTo(communityId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.title()).isEqualTo("Integration Test Link Post");
        assertThat(response.content()).isNull();
        assertThat(response.url()).isEqualTo("https://spring.io/projects/spring-boot");
        assertThat(response.type()).isEqualTo(PostType.LINK);

        verify(communityClient).verifyCommunityExists(communityId, token);

        Optional<Post> found = postRepository.findById(response.id());
        assertThat(found).isPresent();
        Post post = found.get();
        assertThat(post.getUrl()).isEqualTo("https://spring.io/projects/spring-boot");
        assertThat(post.getContent()).isNull();
    }

    @Test
    void shouldNotPersistPostWhenCommunityVerificationFails() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "Post Never Persisted",
            PostType.TEXT,
            "Some content",
            null
        );

        Mockito.doThrow(new CommunityNotFoundException("Community not found"))
            .when(communityClient).verifyCommunityExists(communityId, token);

        assertThatThrownBy(() -> postService.createPost(request, authorId, token))
            .isInstanceOf(CommunityNotFoundException.class);

        assertThat(postRepository.count()).isEqualTo(0L);
    }

    @Test
    void shouldGetExistingPostById() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Post post = new Post(communityId, authorId, "Title for Get", "Content for Get", null, PostType.TEXT);
        Post saved = postRepository.save(post);

        PostResponse response = postService.getPost(saved.getId());

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.communityId()).isEqualTo(communityId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.title()).isEqualTo("Title for Get");
        assertThat(response.content()).isEqualTo("Content for Get");
        assertThat(response.type()).isEqualTo(PostType.TEXT);

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldThrowPostNotFoundExceptionWhenPostDoesNotExistInDatabase() {
        UUID nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> postService.getPost(nonExistentId))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessageContaining(nonExistentId.toString());

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldFilterPostsByCommunityId() {
        UUID communityA = UUID.randomUUID();
        UUID communityB = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Post postA1 = postRepository.save(new Post(communityA, authorId, "Post A1", "Content", null, PostType.TEXT));
        Post postA2 = postRepository.save(new Post(communityA, authorId, "Post A2", "Content", null, PostType.TEXT));
        postRepository.save(new Post(communityB, authorId, "Post B1", "Content", null, PostType.TEXT));

        PagedResponse<PostResponse> response = postService.getPosts(communityA, "new", 0, 20);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.content()).allMatch(p -> p.communityId().equals(communityA));
        assertThat(response.content().stream().map(PostResponse::id).toList())
            .containsExactlyInAnyOrder(postA1.getId(), postA2.getId());

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldSortPostsNewestFirst() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-09-18T10:00:00Z");

        Post oldest = new Post(communityId, authorId, "Oldest", "Content", null, PostType.TEXT);
        oldest.setCreatedAt(baseTime);
        oldest.setUpdatedAt(baseTime);
        postRepository.save(oldest);

        Post middle = new Post(communityId, authorId, "Middle", "Content", null, PostType.TEXT);
        middle.setCreatedAt(baseTime.plusSeconds(60));
        middle.setUpdatedAt(baseTime.plusSeconds(60));
        postRepository.save(middle);

        Post newest = new Post(communityId, authorId, "Newest", "Content", null, PostType.TEXT);
        newest.setCreatedAt(baseTime.plusSeconds(120));
        newest.setUpdatedAt(baseTime.plusSeconds(120));
        postRepository.save(newest);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 0, 20);

        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0).title()).isEqualTo("Newest");
        assertThat(response.content().get(1).title()).isEqualTo("Middle");
        assertThat(response.content().get(2).title()).isEqualTo("Oldest");

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldDeterministicallyOrderEqualTimestampsByIdDesc() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant fixedTime = Instant.parse("2026-09-18T12:00:00Z");

        Post postA = new Post(communityId, authorId, "Post A", "Content A", null, PostType.TEXT);
        postA.setCreatedAt(fixedTime);
        postA.setUpdatedAt(fixedTime);
        Post savedA = postRepository.save(postA);

        Post postB = new Post(communityId, authorId, "Post B", "Content B", null, PostType.TEXT);
        postB.setCreatedAt(fixedTime);
        postB.setUpdatedAt(fixedTime);
        Post savedB = postRepository.save(postB);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 0, 20);

        UUID expectedFirst = savedA.getId().toString().compareTo(savedB.getId().toString()) > 0 ? savedA.getId() : savedB.getId();
        UUID expectedSecond = savedA.getId().toString().compareTo(savedB.getId().toString()) > 0 ? savedB.getId() : savedA.getId();

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).id()).isEqualTo(expectedFirst);
        assertThat(response.content().get(1).id()).isEqualTo(expectedSecond);

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldPaginatePostsAcrossPages() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-09-18T10:00:00Z");

        for (int i = 0; i < 5; i++) {
            Post post = new Post(communityId, authorId, "Post " + i, "Content", null, PostType.TEXT);
            post.setCreatedAt(baseTime.plusSeconds(i * 10));
            post.setUpdatedAt(baseTime.plusSeconds(i * 10));
            postRepository.save(post);
        }

        PagedResponse<PostResponse> page0 = postService.getPosts(communityId, "new", 0, 2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.totalElements()).isEqualTo(5L);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.last()).isFalse();
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.content().get(0).title()).isEqualTo("Post 4");
        assertThat(page0.content().get(1).title()).isEqualTo("Post 3");

        PagedResponse<PostResponse> page1 = postService.getPosts(communityId, "new", 1, 2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(2);
        assertThat(page1.totalElements()).isEqualTo(5L);
        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page1.last()).isFalse();
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.content().get(0).title()).isEqualTo("Post 2");
        assertThat(page1.content().get(1).title()).isEqualTo("Post 1");

        PagedResponse<PostResponse> page2 = postService.getPosts(communityId, "new", 2, 2);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.size()).isEqualTo(2);
        assertThat(page2.totalElements()).isEqualTo(5L);
        assertThat(page2.totalPages()).isEqualTo(3);
        assertThat(page2.last()).isTrue();
        assertThat(page2.content()).hasSize(1);
        assertThat(page2.content().get(0).title()).isEqualTo("Post 0");

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldReturnEmptyPageForUnknownCommunityWithoutCallingCommunityService() {
        UUID unknownCommunityId = UUID.randomUUID();

        PagedResponse<PostResponse> response = postService.getPosts(unknownCommunityId, "new", 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(0L);
        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.last()).isTrue();

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldSortPostsByScoreDescendingWithDeterministicTieBreakers() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-09-18T10:00:00Z");

        // Post with lower score
        Post lowScore = new Post(communityId, authorId, "Low Score", "Content", null, PostType.TEXT);
        lowScore.setScore(5);
        lowScore.setCreatedAt(baseTime.plusSeconds(300));
        lowScore.setUpdatedAt(baseTime.plusSeconds(300));
        postRepository.save(lowScore);

        // Post with higher score, older createdAt
        Post highScoreOlder = new Post(communityId, authorId, "High Score Older", "Content", null, PostType.TEXT);
        highScoreOlder.setScore(20);
        highScoreOlder.setCreatedAt(baseTime);
        highScoreOlder.setUpdatedAt(baseTime);
        postRepository.save(highScoreOlder);

        // Two posts with higher score and identical newer createdAt
        Instant identicalTime = baseTime.plusSeconds(100);
        Post highScoreNewerA = new Post(communityId, authorId, "High Score Newer A", "Content", null, PostType.TEXT);
        highScoreNewerA.setScore(20);
        highScoreNewerA.setCreatedAt(identicalTime);
        highScoreNewerA.setUpdatedAt(identicalTime);
        Post savedA = postRepository.save(highScoreNewerA);

        Post highScoreNewerB = new Post(communityId, authorId, "High Score Newer B", "Content", null, PostType.TEXT);
        highScoreNewerB.setScore(20);
        highScoreNewerB.setCreatedAt(identicalTime);
        highScoreNewerB.setUpdatedAt(identicalTime);
        Post savedB = postRepository.save(highScoreNewerB);

        UUID expectedFirst = savedA.getId().toString().compareTo(savedB.getId().toString()) > 0 ? savedA.getId() : savedB.getId();
        UUID expectedSecond = savedA.getId().toString().compareTo(savedB.getId().toString()) > 0 ? savedB.getId() : savedA.getId();

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "top", 0, 20);

        assertThat(response.content()).hasSize(4);
        assertThat(response.content().get(0).id()).isEqualTo(expectedFirst);
        assertThat(response.content().get(1).id()).isEqualTo(expectedSecond);
        assertThat(response.content().get(2).id()).isEqualTo(highScoreOlder.getId());
        assertThat(response.content().get(3).id()).isEqualTo(lowScore.getId());
    }

    @Test
    void shouldCacheDefaultNewAndTopPagesInRedis() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Post post = new Post(communityId, authorId, "Feed Post", "Content", null, PostType.TEXT);
        post.setScore(10);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        String newKey = postFeedCache.buildKey(communityId, "new");
        String topKey = postFeedCache.buildKey(communityId, "top");

        // Initially no cache
        assertThat(redisTemplate.hasKey(newKey)).isFalse();
        assertThat(redisTemplate.hasKey(topKey)).isFalse();

        // First call populates new cache
        PagedResponse<PostResponse> newFeed1 = postService.getPosts(communityId, "new", 0, 20);
        assertThat(redisTemplate.hasKey(newKey)).isTrue();
        assertThat(redisTemplate.getExpire(newKey)).isGreaterThan(0);

        // First call populates top cache
        PagedResponse<PostResponse> topFeed1 = postService.getPosts(communityId, "top", 0, 20);
        assertThat(redisTemplate.hasKey(topKey)).isTrue();
        assertThat(redisTemplate.getExpire(topKey)).isGreaterThan(0);

        // Second call retrieves from cache
        PagedResponse<PostResponse> newFeed2 = postService.getPosts(communityId, "new", 0, 20);
        assertThat(newFeed2.content()).hasSize(1);
        assertThat(newFeed2.content().getFirst().id()).isEqualTo(post.getId());

        PagedResponse<PostResponse> topFeed2 = postService.getPosts(communityId, "top", 0, 20);
        assertThat(topFeed2.content()).hasSize(1);
        assertThat(topFeed2.content().getFirst().id()).isEqualTo(post.getId());
    }

    @Test
    void shouldEvictNewAndTopCachesWhenPostIsCreated() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        String newKey = postFeedCache.buildKey(communityId, "new");
        String topKey = postFeedCache.buildKey(communityId, "top");

        // Manually set cache entries
        redisTemplate.opsForValue().set(newKey, "dummy-new");
        redisTemplate.opsForValue().set(topKey, "dummy-top");
        assertThat(redisTemplate.hasKey(newKey)).isTrue();
        assertThat(redisTemplate.hasKey(topKey)).isTrue();

        // Create a post
        CreatePostRequest request = new CreatePostRequest(
            communityId, "Evict Post", PostType.TEXT, "Content", null
        );
        postService.createPost(request, authorId, token);

        // Both caches should be evicted
        assertThat(redisTemplate.hasKey(newKey)).isFalse();
        assertThat(redisTemplate.hasKey(topKey)).isFalse();
    }

    @Test
    void shouldBypassCacheForNonDefaultPageOrSize() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Post post = new Post(communityId, authorId, "Feed Post", "Content", null, PostType.TEXT);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        // Non-default page
        postService.getPosts(communityId, "new", 1, 20);
        assertThat(redisTemplate.hasKey("posts:" + communityId + ":new:1:20")).isFalse();

        // Non-default size
        postService.getPosts(communityId, "new", 0, 10);
        assertThat(redisTemplate.hasKey("posts:" + communityId + ":new:0:10")).isFalse();
    }

    @Test
    void shouldIncrementCommentCountAndEvictFeedCachesOnCommentCreatedEvent() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Post post = new Post(communityId, authorId, "Post For Comment", "Content", null, PostType.TEXT);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        post = postRepository.saveAndFlush(post);
        assertThat(post.getCommentCount()).isEqualTo(0);

        String newKey = postFeedCache.buildKey(communityId, "new");
        String topKey = postFeedCache.buildKey(communityId, "top");
        redisTemplate.opsForValue().set(newKey, "cached-new");
        redisTemplate.opsForValue().set(topKey, "cached-top");

        UUID eventId = UUID.randomUUID();
        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            post.getId(),
            null,
            UUID.randomUUID(),
            authorId,
            "POST_COMMENT",
            Instant.now()
        );

        commentCountService.processCommentCreated(event);

        Post updatedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(updatedPost.getCommentCount()).isEqualTo(1);
        assertThat(processedCommentEventRepository.existsById(eventId)).isTrue();
        assertThat(redisTemplate.hasKey(newKey)).isFalse();
        assertThat(redisTemplate.hasKey(topKey)).isFalse();
    }

    @Test
    void shouldBeIdempotentOnDuplicateCommentCreatedEvent() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Post post = new Post(communityId, authorId, "Post For Idempotency", "Content", null, PostType.TEXT);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
        post = postRepository.saveAndFlush(post);

        UUID eventId = UUID.randomUUID();
        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            post.getId(),
            null,
            UUID.randomUUID(),
            authorId,
            "POST_COMMENT",
            Instant.now()
        );

        commentCountService.processCommentCreated(event);

        Post firstPass = postRepository.findById(post.getId()).orElseThrow();
        assertThat(firstPass.getCommentCount()).isEqualTo(1);
        assertThat(processedCommentEventRepository.count()).isEqualTo(1L);

        // Process duplicate event
        commentCountService.processCommentCreated(event);

        Post secondPass = postRepository.findById(post.getId()).orElseThrow();
        assertThat(secondPass.getCommentCount()).isEqualTo(1);
        assertThat(processedCommentEventRepository.count()).isEqualTo(1L);
    }
}
