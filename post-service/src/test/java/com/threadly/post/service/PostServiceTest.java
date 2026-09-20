package com.threadly.post.service;

import com.threadly.post.cache.PostFeedCache;
import com.threadly.post.client.CommunityClient;
import com.threadly.post.dto.request.CreatePostRequest;
import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostType;
import com.threadly.post.exception.CommunityNotFoundException;
import com.threadly.post.exception.CommunityServiceUnavailableException;
import com.threadly.post.exception.InvalidPaginationException;
import com.threadly.post.exception.InvalidSortException;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommunityClient communityClient;

    @Mock
    private PostFeedCache postFeedCache;

    @InjectMocks
    private PostService postService;

    @Test
    void shouldCreateTextPostSuccessfully() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "dummy.jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "  Discussion on Architecture  ",
            PostType.TEXT,
            "  Clean Architecture in Spring Boot  ",
            null
        );

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(UUID.randomUUID());
            post.setCreatedAt(Instant.now());
            post.setUpdatedAt(Instant.now());
            return post;
        });

        PostResponse response = postService.createPost(request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.communityId()).isEqualTo(communityId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.title()).isEqualTo("Discussion on Architecture");
        assertThat(response.content()).isEqualTo("Clean Architecture in Spring Boot");
        assertThat(response.url()).isNull();
        assertThat(response.type()).isEqualTo(PostType.TEXT);
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.commentCount()).isEqualTo(0);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        // Verify HTTP call was executed before repository save
        InOrder inOrder = Mockito.inOrder(communityClient, postRepository);
        inOrder.verify(communityClient).verifyCommunityExists(communityId, token);
        inOrder.verify(postRepository).save(any(Post.class));
        verify(postFeedCache).evict(communityId);
    }

    @Test
    void shouldCreateLinkPostSuccessfully() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "dummy.jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "  Spring Release Notes  ",
            PostType.LINK,
            null,
            "  https://spring.io/blog/release-notes  "
        );

        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(UUID.randomUUID());
            post.setCreatedAt(Instant.now());
            post.setUpdatedAt(Instant.now());
            return post;
        });

        PostResponse response = postService.createPost(request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.communityId()).isEqualTo(communityId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.title()).isEqualTo("Spring Release Notes");
        assertThat(response.content()).isNull();
        assertThat(response.url()).isEqualTo("https://spring.io/blog/release-notes");
        assertThat(response.type()).isEqualTo(PostType.LINK);
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.commentCount()).isEqualTo(0);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post saved = postCaptor.getValue();
        assertThat(saved.getUrl()).isEqualTo("https://spring.io/blog/release-notes");
        verify(postFeedCache).evict(communityId);
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionWhenCommunityDoesNotExist() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "dummy.jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "Title",
            PostType.TEXT,
            "Content",
            null
        );

        Mockito.doThrow(new CommunityNotFoundException("Community not found with id: " + communityId))
            .when(communityClient).verifyCommunityExists(communityId, token);

        assertThatThrownBy(() -> postService.createPost(request, authorId, token))
            .isInstanceOf(CommunityNotFoundException.class)
            .hasMessageContaining(communityId.toString());

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldThrowCommunityServiceUnavailableExceptionWhenCommunityServiceIsDown() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "dummy.jwt.token";

        CreatePostRequest request = new CreatePostRequest(
            communityId,
            "Title",
            PostType.TEXT,
            "Content",
            null
        );

        Mockito.doThrow(new CommunityServiceUnavailableException("Community service is unavailable"))
            .when(communityClient).verifyCommunityExists(communityId, token);

        assertThatThrownBy(() -> postService.createPost(request, authorId, token))
            .isInstanceOf(CommunityServiceUnavailableException.class)
            .hasMessageContaining("Community service is unavailable");

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void shouldGetPostByIdSuccessfully() {
        UUID postId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant now = Instant.now();

        Post post = new Post();
        post.setId(postId);
        post.setCommunityId(communityId);
        post.setAuthorId(authorId);
        post.setTitle("Test Post");
        post.setContent("Test Content");
        post.setType(PostType.TEXT);
        post.setScore(5);
        post.setCommentCount(2);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        PostResponse response = postService.getPost(postId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(postId);
        assertThat(response.communityId()).isEqualTo(communityId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.title()).isEqualTo("Test Post");
        assertThat(response.content()).isEqualTo("Test Content");
        assertThat(response.type()).isEqualTo(PostType.TEXT);
        assertThat(response.score()).isEqualTo(5);
        assertThat(response.commentCount()).isEqualTo(2);

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldThrowPostNotFoundExceptionWhenPostDoesNotExist() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(postId))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessageContaining(postId.toString());

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldGetPostsForCommunityWithDeterministicOrdering() {
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant now = Instant.now();

        Post post1 = new Post();
        post1.setId(UUID.randomUUID());
        post1.setCommunityId(communityId);
        post1.setAuthorId(authorId);
        post1.setTitle("Post 1");
        post1.setContent("Content 1");
        post1.setType(PostType.TEXT);
        post1.setCreatedAt(now);
        post1.setUpdatedAt(now);

        Page<Post> page = new PageImpl<>(List.of(post1), PageRequest.of(0, 20), 1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(postFeedCache.get(communityId, "new")).thenReturn(Optional.empty());
        when(postRepository.findByCommunityId(eq(communityId), pageableCaptor.capture()))
            .thenReturn(page);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().title()).isEqualTo("Post 1");
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.last()).isTrue();

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
        assertThat(capturedPageable.getPageSize()).isEqualTo(20);
        assertThat(capturedPageable.getSort()).isEqualTo(
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        verify(postFeedCache).put(eq(communityId), eq("new"), eq(response));
        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }

    @Test
    void shouldGetPostsForCommunityWithTopOrdering() {
        UUID communityId = UUID.randomUUID();
        Page<Post> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(postFeedCache.get(communityId, "top")).thenReturn(Optional.empty());
        when(postRepository.findByCommunityId(eq(communityId), pageableCaptor.capture()))
            .thenReturn(emptyPage);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "top", 0, 20);

        assertThat(response).isNotNull();
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getSort()).isEqualTo(
            Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        verify(postFeedCache).put(eq(communityId), eq("top"), eq(response));
    }

    @Test
    void shouldReturnCachedPostsOnCacheHit() {
        UUID communityId = UUID.randomUUID();
        PagedResponse<PostResponse> cachedResponse = new PagedResponse<>(
            List.of(), 0, 20, 0, 0, true
        );
        when(postFeedCache.get(communityId, "new")).thenReturn(Optional.of(cachedResponse));

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 0, 20);

        assertThat(response).isSameAs(cachedResponse);
        verify(postRepository, never()).findByCommunityId(any(), any());
        verify(postFeedCache, never()).put(any(), any(), any());
    }

    @Test
    void shouldBypassCacheWhenPageIsNotZero() {
        UUID communityId = UUID.randomUUID();
        Page<Post> emptyPage = new PageImpl<>(List.of(), PageRequest.of(1, 20), 0);
        when(postRepository.findByCommunityId(eq(communityId), any(Pageable.class)))
            .thenReturn(emptyPage);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 1, 20);

        assertThat(response).isNotNull();
        verify(postFeedCache, never()).get(any(), any());
        verify(postFeedCache, never()).put(any(), any(), any());
    }

    @Test
    void shouldBypassCacheWhenSizeIsNotTwenty() {
        UUID communityId = UUID.randomUUID();
        Page<Post> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);
        when(postRepository.findByCommunityId(eq(communityId), any(Pageable.class)))
            .thenReturn(emptyPage);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 0, 50);

        assertThat(response).isNotNull();
        verify(postFeedCache, never()).get(any(), any());
        verify(postFeedCache, never()).put(any(), any(), any());
    }

    @Test
    void shouldNormalizeSortCaseAndWhitespace() {
        UUID communityId = UUID.randomUUID();
        Page<Post> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(postFeedCache.get(communityId, "new")).thenReturn(Optional.empty());
        when(postRepository.findByCommunityId(eq(communityId), any(Pageable.class)))
            .thenReturn(emptyPage);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "  NeW  ", 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        verify(postRepository).findByCommunityId(eq(communityId), any(Pageable.class));
        verify(postFeedCache).get(communityId, "new");
    }

    @Test
    void shouldThrowInvalidSortExceptionWhenSortIsUnsupported() {
        UUID communityId = UUID.randomUUID();

        assertThatThrownBy(() -> postService.getPosts(communityId, "hot", 0, 20))
            .isInstanceOf(InvalidSortException.class)
            .hasMessageContaining("hot");

        assertThatThrownBy(() -> postService.getPosts(communityId, "unknown", 0, 20))
            .isInstanceOf(InvalidSortException.class);

        assertThatThrownBy(() -> postService.getPosts(communityId, null, 0, 20))
            .isInstanceOf(InvalidSortException.class);

        verify(postRepository, never()).findByCommunityId(any(), any());
        verify(postFeedCache, never()).get(any(), any());
    }

    @Test
    void shouldThrowInvalidPaginationExceptionWhenPageIsNegative() {
        UUID communityId = UUID.randomUUID();

        assertThatThrownBy(() -> postService.getPosts(communityId, "new", -1, 20))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("page");
            });

        verify(postRepository, never()).findByCommunityId(any(), any());
    }

    @Test
    void shouldThrowInvalidPaginationExceptionWhenSizeIsLessThanOne() {
        UUID communityId = UUID.randomUUID();

        assertThatThrownBy(() -> postService.getPosts(communityId, "new", 0, 0))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("size");
            });

        verify(postRepository, never()).findByCommunityId(any(), any());
    }

    @Test
    void shouldThrowInvalidPaginationExceptionWhenSizeExceedsOneHundred() {
        UUID communityId = UUID.randomUUID();

        assertThatThrownBy(() -> postService.getPosts(communityId, "new", 0, 101))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("size");
            });

        verify(postRepository, never()).findByCommunityId(any(), any());
    }

    @Test
    void shouldReturnEmptyPageWhenCommunityHasNoPosts() {
        UUID communityId = UUID.randomUUID();
        Page<Post> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(postFeedCache.get(communityId, "new")).thenReturn(Optional.empty());
        when(postRepository.findByCommunityId(eq(communityId), any(Pageable.class)))
            .thenReturn(emptyPage);

        PagedResponse<PostResponse> response = postService.getPosts(communityId, "new", 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0L);
        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.last()).isTrue();

        verify(communityClient, never()).verifyCommunityExists(any(), any());
    }
}
