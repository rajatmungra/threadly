package com.threadly.post.controller;

import com.threadly.post.config.SecurityConfig;
import com.threadly.post.dto.request.CreatePostRequest;
import com.threadly.post.dto.response.PagedResponse;
import com.threadly.post.dto.response.PostResponse;
import com.threadly.post.entity.PostType;
import com.threadly.post.exception.CommunityNotFoundException;
import com.threadly.post.exception.CommunityServiceUnavailableException;
import com.threadly.post.exception.CustomAuthenticationEntryPoint;
import com.threadly.post.exception.GlobalExceptionHandler;
import com.threadly.post.exception.InvalidPaginationException;
import com.threadly.post.exception.InvalidSortException;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PostController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    void shouldCreateTextPostWhenAuthenticatedWithValidJwt() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        PostResponse response = new PostResponse(
            postId,
            communityId,
            authorId,
            "First Post Title",
            "This is the content of the post.",
            null,
            PostType.TEXT,
            0,
            0,
            createdAt,
            createdAt
        );

        when(postService.createPost(any(CreatePostRequest.class), eq(authorId), anyString()))
            .thenReturn(response);

        String requestBody = """
            {
                "communityId": "%s",
                "title": "First Post Title",
                "content": "This is the content of the post.",
                "type": "TEXT"
            }
            """.formatted(communityId);

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(postId.toString()))
            .andExpect(jsonPath("$.communityId").value(communityId.toString()))
            .andExpect(jsonPath("$.authorId").value(authorId.toString()))
            .andExpect(jsonPath("$.title").value("First Post Title"))
            .andExpect(jsonPath("$.content").value("This is the content of the post."))
            .andExpect(jsonPath("$.url").doesNotExist())
            .andExpect(jsonPath("$.type").value("TEXT"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.commentCount").value(0))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());

        verify(postService).createPost(any(CreatePostRequest.class), eq(authorId), anyString());
    }

    @Test
    void shouldCreateLinkPostWhenAuthenticatedWithValidJwt() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        PostResponse response = new PostResponse(
            postId,
            communityId,
            authorId,
            "Interesting Link Title",
            null,
            "https://spring.io",
            PostType.LINK,
            0,
            0,
            createdAt,
            createdAt
        );

        when(postService.createPost(any(CreatePostRequest.class), eq(authorId), anyString()))
            .thenReturn(response);

        String requestBody = """
            {
                "communityId": "%s",
                "title": "Interesting Link Title",
                "url": "https://spring.io",
                "type": "LINK"
            }
            """.formatted(communityId);

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(postId.toString()))
            .andExpect(jsonPath("$.communityId").value(communityId.toString()))
            .andExpect(jsonPath("$.authorId").value(authorId.toString()))
            .andExpect(jsonPath("$.title").value("Interesting Link Title"))
            .andExpect(jsonPath("$.content").doesNotExist())
            .andExpect(jsonPath("$.url").value("https://spring.io"))
            .andExpect(jsonPath("$.type").value("LINK"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.commentCount").value(0));

        verify(postService).createPost(any(CreatePostRequest.class), eq(authorId), anyString());
    }

    @Test
    void shouldReturn401WhenRequestingWithoutToken() throws Exception {
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Unauthenticated Post",
                "content": "Some content",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenJwtSubjectIsNotAValidUuid() throws Exception {
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Post Title",
                "content": "Some content",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid-format")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn404WhenCommunityNotFound() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();

        when(postService.createPost(any(CreatePostRequest.class), eq(authorId), anyString()))
            .thenThrow(new CommunityNotFoundException("Community not found with id: " + communityId));

        String requestBody = """
            {
                "communityId": "%s",
                "title": "Post for Nonexistent Community",
                "content": "Valid content",
                "type": "TEXT"
            }
            """.formatted(communityId);

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Community not found with id: " + communityId));
    }

    @Test
    void shouldReturn503WhenCommunityServiceUnavailable() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();

        when(postService.createPost(any(CreatePostRequest.class), eq(authorId), anyString()))
            .thenThrow(new CommunityServiceUnavailableException("Community service is unavailable"));

        String requestBody = """
            {
                "communityId": "%s",
                "title": "Post During Outage",
                "content": "Valid content",
                "type": "TEXT"
            }
            """.formatted(communityId);

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.code").value("COMMUNITY_SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Community service is unavailable"));
    }

    @Test
    void shouldReturn400WhenTitleIsBlank() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "   ",
                "content": "Some content",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void shouldReturn400WhenTitleIsTooShort() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "ab",
                "content": "Some content",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void shouldReturn400WhenTitleExceedsMaxLength() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "%s",
                "content": "Some content",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID(), "a".repeat(301));

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void shouldReturn400WhenCommunityIdIsNull() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": null,
                "title": "Valid Title",
                "content": "Some content",
                "type": "TEXT"
            }
            """;

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.communityId").exists());
    }

    @Test
    void shouldReturn400WhenTypeIsNull() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Title",
                "content": "Some content",
                "type": null
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void shouldReturn400WhenTextPostHasNoContent() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Title",
                "content": "   ",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.content").value("Content is required for text posts"));
    }

    @Test
    void shouldReturn400WhenTextPostHasUrl() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Title",
                "content": "Valid text content",
                "url": "https://example.com",
                "type": "TEXT"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.url").value("URL must not be provided for text posts"));
    }

    @Test
    void shouldReturn400WhenLinkPostHasNoUrl() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Title",
                "url": "  ",
                "type": "LINK"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.url").value("URL is required for link posts"));
    }

    @Test
    void shouldReturn400WhenLinkPostHasInvalidUrl() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Title",
                "url": "not-a-valid-url",
                "type": "LINK"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.url").value("URL must be a valid HTTP or HTTPS URL"));
    }

    @Test
    void shouldGetPostWhenExistsAndAuthenticated() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant now = Instant.now();

        PostResponse response = new PostResponse(
            postId,
            communityId,
            authorId,
            "Sample Title",
            "Sample Content",
            null,
            PostType.TEXT,
            0,
            0,
            now,
            now
        );

        when(postService.getPost(postId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(postId.toString()))
            .andExpect(jsonPath("$.communityId").value(communityId.toString()))
            .andExpect(jsonPath("$.authorId").value(authorId.toString()))
            .andExpect(jsonPath("$.title").value("Sample Title"))
            .andExpect(jsonPath("$.content").value("Sample Content"))
            .andExpect(jsonPath("$.type").value("TEXT"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.commentCount").value(0))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());

        verify(postService).getPost(postId);
    }

    @Test
    void shouldReturn404WhenPostNotFound() throws Exception {
        UUID postId = UUID.randomUUID();
        when(postService.getPost(postId)).thenThrow(new PostNotFoundException("Post not found with ID: " + postId));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                .with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Post not found with ID: " + postId));
    }

    @Test
    void shouldReturn401WhenGetPostUnauthenticated() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenGetPostWithMalformedUuid() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}", "not-a-uuid")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.postId").value("Invalid value for parameter 'postId'"));
    }

    @Test
    void shouldGetCommunityFeedWithDefaultParameters() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant now = Instant.now();

        PostResponse post = new PostResponse(
            postId,
            communityId,
            authorId,
            "Feed Post",
            "Content",
            null,
            PostType.TEXT,
            0,
            0,
            now,
            now
        );

        PagedResponse<PostResponse> pagedResponse = new PagedResponse<>(
            List.of(post),
            0,
            20,
            1L,
            1,
            true
        );

        when(postService.getPosts(eq(communityId), eq("new"), eq(0), eq(20)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", communityId.toString())
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(postId.toString()))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.last").value(true));

        verify(postService).getPosts(eq(communityId), eq("new"), eq(0), eq(20));
    }

    @Test
    void shouldGetCommunityFeedWithCustomParameters() throws Exception {
        UUID communityId = UUID.randomUUID();
        PagedResponse<PostResponse> pagedResponse = new PagedResponse<>(
            List.of(),
            1,
            10,
            0L,
            0,
            true
        );

        when(postService.getPosts(eq(communityId), eq("new"), eq(1), eq(10)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", communityId.toString())
                .param("sort", "new")
                .param("page", "1")
                .param("size", "10")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10));

        verify(postService).getPosts(eq(communityId), eq("new"), eq(1), eq(10));
    }

    @Test
    void shouldGetCommunityFeedWithSortTop() throws Exception {
        UUID communityId = UUID.randomUUID();
        PagedResponse<PostResponse> pagedResponse = new PagedResponse<>(
            List.of(),
            0,
            20,
            0L,
            0,
            true
        );

        when(postService.getPosts(eq(communityId), eq("top"), eq(0), eq(20)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", communityId.toString())
                .param("sort", "top")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20));

        verify(postService).getPosts(eq(communityId), eq("top"), eq(0), eq(20));
    }

    @Test
    void shouldReturn400WhenSortIsUnsupported() throws Exception {
        UUID communityId = UUID.randomUUID();
        when(postService.getPosts(eq(communityId), eq("hot"), eq(0), eq(20)))
            .thenThrow(new InvalidSortException("Unsupported sort: hot. Only 'new' and 'top' are supported"));

        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", communityId.toString())
                .param("sort", "hot")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_SORT"))
            .andExpect(jsonPath("$.message").value("Unsupported sort: hot. Only 'new' and 'top' are supported"));
    }

    @Test
    void shouldReturn400WhenPaginationIsInvalid() throws Exception {
        UUID communityId = UUID.randomUUID();
        when(postService.getPosts(eq(communityId), anyString(), eq(-1), eq(20)))
            .thenThrow(new InvalidPaginationException(
                "Invalid pagination parameters",
                Map.of("page", "Page index must not be less than zero")
            ));

        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", communityId.toString())
                .param("page", "-1")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.page").value("Page index must not be less than zero"));
    }

    @Test
    void shouldReturn400WhenCommunityIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.communityId").value("Required parameter 'communityId' is missing"));
    }

    @Test
    void shouldReturn400WhenCommunityIdIsMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", "invalid-uuid")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.communityId").value("Invalid value for parameter 'communityId'"));
    }

    @Test
    void shouldReturn401WhenGetPostsUnauthenticated() throws Exception {
        UUID communityId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/posts")
                .param("communityId", communityId.toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenCreatePostPayloadIsMalformedJson() throws Exception {
        UUID authorId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{malformed_json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Malformed request payload"))
            .andExpect(jsonPath("$.path").value("/api/v1/posts"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenCreatePostHasUnsupportedPostType() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "communityId": "%s",
                "title": "Valid Title",
                "content": "Valid text content",
                "type": "UNSUPPORTED_TYPE"
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/posts")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Malformed request payload"))
            .andExpect(jsonPath("$.path").value("/api/v1/posts"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
