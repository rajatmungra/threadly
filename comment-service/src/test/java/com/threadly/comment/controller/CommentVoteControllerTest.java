package com.threadly.comment.controller;

import com.threadly.comment.config.SecurityConfig;
import com.threadly.comment.dto.response.VoteResponse;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.exception.CustomAuthenticationEntryPoint;
import com.threadly.comment.exception.GlobalExceptionHandler;
import com.threadly.comment.service.CommentVoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommentVoteController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class CommentVoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentVoteService commentVoteService;

    @Test
    void shouldSetUpvoteWhenAuthenticatedWithValidJwt() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(commentVoteService.setVote(commentId, userId, 1))
            .thenReturn(new VoteResponse(commentId, 1, 1));

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commentId").value(commentId.toString()))
            .andExpect(jsonPath("$.value").value(1))
            .andExpect(jsonPath("$.score").value(1));

        verify(commentVoteService).setVote(commentId, userId, 1);
    }

    @Test
    void shouldSetDownvoteWhenAuthenticatedWithValidJwt() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(commentVoteService.setVote(commentId, userId, -1))
            .thenReturn(new VoteResponse(commentId, -1, -1));

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": -1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commentId").value(commentId.toString()))
            .andExpect(jsonPath("$.value").value(-1))
            .andExpect(jsonPath("$.score").value(-1));

        verify(commentVoteService).setVote(commentId, userId, -1);
    }

    @Test
    void shouldReturn401WhenPutVoteWithoutToken() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": 1
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenPutVoteWithMalformedJwtSubject() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid-subject")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": 1
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenPutVoteWithInvalidValue() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.value").value("Vote value must be 1 (upvote) or -1 (downvote)"));
    }

    @Test
    void shouldReturn400WhenPutVoteWithNullValue() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.value").value("Vote value is required"));
    }

    @Test
    void shouldReturn400WhenPutVoteWithMalformedCommentId() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", "not-a-valid-uuid")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.commentId").value("Invalid value for parameter 'commentId'"));
    }

    @Test
    void shouldReturn404WhenPutVoteForNonExistentComment() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(commentVoteService.setVote(commentId, userId, 1))
            .thenThrow(new CommentNotFoundException("Comment not found with id: " + commentId));

        mockMvc.perform(put("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "value": 1
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Comment not found with id: " + commentId));
    }

    @Test
    void shouldRemoveVoteWhenAuthenticatedWithValidJwt() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doNothing().when(commentVoteService).removeVote(commentId, userId);

        mockMvc.perform(delete("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(commentVoteService).removeVote(commentId, userId);
    }

    @Test
    void shouldReturn401WhenDeleteVoteWithoutToken() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/comments/{commentId}/votes", commentId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenDeleteVoteWithMalformedJwtSubject() throws Exception {
        UUID commentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid-subject"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenDeleteVoteWithMalformedCommentId() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/comments/{commentId}/votes", "not-a-valid-uuid")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.commentId").value("Invalid value for parameter 'commentId'"));
    }

    @Test
    void shouldReturn404WhenDeleteVoteForNonExistentComment() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doThrow(new CommentNotFoundException("Comment not found with id: " + commentId))
            .when(commentVoteService).removeVote(commentId, userId);

        mockMvc.perform(delete("/api/v1/comments/{commentId}/votes", commentId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Comment not found with id: " + commentId));
    }
}
