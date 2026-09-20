package com.threadly.comment.controller;

import com.threadly.comment.config.SecurityConfig;
import com.threadly.comment.dto.request.CreateCommentRequest;
import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.exception.CustomAuthenticationEntryPoint;
import com.threadly.comment.exception.GlobalExceptionHandler;
import com.threadly.comment.exception.InvalidPaginationException;
import com.threadly.comment.exception.InvalidParentCommentException;
import com.threadly.comment.exception.PostNotFoundException;
import com.threadly.comment.exception.PostServiceUnavailableException;
import com.threadly.comment.service.CommentService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {CommentController.class, CommentReplyController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @Test
    void shouldCreateRootCommentWhenAuthenticatedWithValidJwt() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.now();

        CommentResponse response = new CommentResponse(
            commentId,
            postId,
            authorId,
            null,
            "This is a root comment",
            0,
            now,
            now
        );

        when(commentService.createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString()))
            .thenReturn(response);

        String requestBody = """
            {
                "content": "This is a root comment",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(commentId.toString()))
            .andExpect(jsonPath("$.postId").value(postId.toString()))
            .andExpect(jsonPath("$.authorId").value(authorId.toString()))
            .andExpect(jsonPath("$.parentCommentId").doesNotExist())
            .andExpect(jsonPath("$.content").value("This is a root comment"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());

        verify(commentService).createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString());
    }

    @Test
    void shouldCreateReplyWhenAuthenticatedWithValidJwt() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        Instant now = Instant.now();

        CommentResponse response = new CommentResponse(
            replyId,
            postId,
            authorId,
            parentCommentId,
            "This is a reply",
            0,
            now,
            now
        );

        when(commentService.createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString()))
            .thenReturn(response);

        String requestBody = """
            {
                "content": "This is a reply",
                "parentCommentId": "%s"
            }
            """.formatted(parentCommentId);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(replyId.toString()))
            .andExpect(jsonPath("$.postId").value(postId.toString()))
            .andExpect(jsonPath("$.authorId").value(authorId.toString()))
            .andExpect(jsonPath("$.parentCommentId").value(parentCommentId.toString()))
            .andExpect(jsonPath("$.content").value("This is a reply"))
            .andExpect(jsonPath("$.score").value(0))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.updatedAt").exists());

        verify(commentService).createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString());
    }

    @Test
    void shouldReturn401WhenRequestingWithoutToken() throws Exception {
        UUID postId = UUID.randomUUID();
        String requestBody = """
            {
                "content": "Unauthenticated comment",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenJwtSubjectIsNotAValidUuid() throws Exception {
        UUID postId = UUID.randomUUID();
        String requestBody = """
            {
                "content": "Valid comment content",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid-format")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn404WhenPostNotFound() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        when(commentService.createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString()))
            .thenThrow(new PostNotFoundException("Post not found with id: " + postId));

        String requestBody = """
            {
                "content": "Valid comment content",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Post not found with id: " + postId));
    }

    @Test
    void shouldReturn503WhenPostServiceUnavailable() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        when(commentService.createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString()))
            .thenThrow(new PostServiceUnavailableException("Post service is unavailable"));

        String requestBody = """
            {
                "content": "Valid comment content",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.code").value("POST_SERVICE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Post service is unavailable"));
    }

    @Test
    void shouldReturn404WhenParentCommentNotFound() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();

        when(commentService.createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString()))
            .thenThrow(new CommentNotFoundException("Comment not found with id: " + parentCommentId));

        String requestBody = """
            {
                "content": "Valid reply content",
                "parentCommentId": "%s"
            }
            """.formatted(parentCommentId);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Comment not found with id: " + parentCommentId));
    }

    @Test
    void shouldReturn400WhenParentCommentBelongsToDifferentPost() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();

        when(commentService.createComment(eq(postId), any(CreateCommentRequest.class), eq(authorId), anyString()))
            .thenThrow(new InvalidParentCommentException("Parent comment does not belong to post: " + postId));

        String requestBody = """
            {
                "content": "Valid reply content",
                "parentCommentId": "%s"
            }
            """.formatted(parentCommentId);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("INVALID_PARENT_COMMENT"))
            .andExpect(jsonPath("$.message").value("Parent comment does not belong to post: " + postId));
    }

    @Test
    void shouldReturn400WhenContentIsBlank() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        String requestBody = """
            {
                "content": "   ",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void shouldReturn400WhenContentExceedsMaxLength() throws Exception {
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        String requestBody = """
            {
                "content": "%s",
                "parentCommentId": null
            }
            """.formatted("a".repeat(10001));

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void shouldReturn400WhenPostIdInPathIsMalformed() throws Exception {
        UUID authorId = UUID.randomUUID();
        String requestBody = """
            {
                "content": "Valid comment",
                "parentCommentId": null
            }
            """;

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", "not-a-valid-uuid")
                .with(jwt().jwt(jwt -> jwt.subject(authorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.postId").value("Invalid value for parameter 'postId'"));
    }

    @Test
    void shouldGetRootCommentsWhenAuthenticated() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.now();

        CommentResponse comment = new CommentResponse(
            commentId,
            postId,
            UUID.randomUUID(),
            null,
            "Root comment",
            0,
            now,
            now
        );

        PagedResponse<CommentResponse> pagedResponse = new PagedResponse<>(
            List.of(comment),
            0,
            20,
            1L,
            1,
            true
        );

        when(commentService.getRootComments(eq(postId), eq(0), eq(20)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(commentId.toString()))
            .andExpect(jsonPath("$.content[0].parentCommentId").doesNotExist())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.last").value(true));

        verify(commentService).getRootComments(eq(postId), eq(0), eq(20));
    }

    @Test
    void shouldReturn401WhenGetRootCommentsUnauthenticated() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenGetRootCommentsWithMalformedPostId() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", "invalid-uuid")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.postId").value("Invalid value for parameter 'postId'"));
    }

    @Test
    void shouldReturn400WhenGetRootCommentsWithInvalidPagination() throws Exception {
        UUID postId = UUID.randomUUID();

        when(commentService.getRootComments(eq(postId), eq(-1), eq(20)))
            .thenThrow(new InvalidPaginationException("Invalid pagination parameters", Map.of("page", "Page index must not be less than zero")));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                .param("page", "-1")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.page").value("Page index must not be less than zero"));
    }

    @Test
    void shouldGetRepliesWhenAuthenticated() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant now = Instant.now();

        CommentResponse reply = new CommentResponse(
            replyId,
            postId,
            UUID.randomUUID(),
            commentId,
            "Direct reply",
            0,
            now,
            now
        );

        PagedResponse<CommentResponse> pagedResponse = new PagedResponse<>(
            List.of(reply),
            0,
            20,
            1L,
            1,
            true
        );

        when(commentService.getReplies(eq(commentId), eq(0), eq(20)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/comments/{commentId}/replies", commentId)
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(replyId.toString()))
            .andExpect(jsonPath("$.content[0].parentCommentId").value(commentId.toString()))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.last").value(true));

        verify(commentService).getReplies(eq(commentId), eq(0), eq(20));
    }

    @Test
    void shouldReturn404WhenGetRepliesForNonExistentComment() throws Exception {
        UUID commentId = UUID.randomUUID();

        when(commentService.getReplies(eq(commentId), eq(0), eq(20)))
            .thenThrow(new CommentNotFoundException("Comment not found with id: " + commentId));

        mockMvc.perform(get("/api/v1/comments/{commentId}/replies", commentId)
                .with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Comment not found with id: " + commentId));
    }

    @Test
    void shouldReturn401WhenGetRepliesUnauthenticated() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/comments/{commentId}/replies", commentId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenGetRepliesWithMalformedCommentId() throws Exception {
        mockMvc.perform(get("/api/v1/comments/{commentId}/replies", "invalid-uuid")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.commentId").value("Invalid value for parameter 'commentId'"));
    }

    @Test
    void shouldReturn400WhenGetRepliesWithInvalidPagination() throws Exception {
        UUID commentId = UUID.randomUUID();

        when(commentService.getReplies(eq(commentId), eq(0), eq(101)))
            .thenThrow(new InvalidPaginationException("Invalid pagination parameters", Map.of("size", "Page size must not exceed 100")));

        mockMvc.perform(get("/api/v1/comments/{commentId}/replies", commentId)
                .param("size", "101")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.size").value("Page size must not exceed 100"));
    }
}
