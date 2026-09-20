package com.threadly.post.controller;

import com.threadly.post.config.SecurityConfig;
import com.threadly.post.dto.response.VoteResponse;
import com.threadly.post.exception.CustomAuthenticationEntryPoint;
import com.threadly.post.exception.GlobalExceptionHandler;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.service.PostVoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PostVoteController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class PostVoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostVoteService postVoteService;

    @Test
    void shouldSetUpvoteSuccessfullyWhenAuthenticated() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(postVoteService.setVote(eq(postId), eq(userId), eq(1)))
            .thenReturn(new VoteResponse(postId, 1, 1));

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postId").value(postId.toString()))
            .andExpect(jsonPath("$.value").value(1))
            .andExpect(jsonPath("$.score").value(1));

        verify(postVoteService).setVote(postId, userId, 1);
    }

    @Test
    void shouldSetDownvoteSuccessfullyWhenAuthenticated() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(postVoteService.setVote(eq(postId), eq(userId), eq(-1)))
            .thenReturn(new VoteResponse(postId, -1, -1));

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": -1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.postId").value(postId.toString()))
            .andExpect(jsonPath("$.value").value(-1))
            .andExpect(jsonPath("$.score").value(-1));

        verify(postVoteService).setVote(postId, userId, -1);
    }

    @Test
    void shouldReturn404WhenPostNotFoundOnSetVote() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(postVoteService.setVote(eq(postId), eq(userId), eq(1)))
            .thenThrow(new PostNotFoundException("Post not found with ID: " + postId));

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 1}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Post not found with ID: " + postId));
    }

    @Test
    void shouldReturn400WhenVoteValueIsInvalid() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.value").value("Vote value must be 1 (upvote) or -1 (downvote)"));
    }

    @Test
    void shouldReturn400WhenVoteValueIsNull() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.value").value("Vote value is required"));
    }

    @Test
    void shouldReturn401WhenSetVoteWithoutToken() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenJwtSubjectIsMalformed() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\": 1}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRemoveVoteSuccessfullyWhenAuthenticated() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(postVoteService).removeVote(postId, userId);
    }

    @Test
    void shouldReturn404WhenPostNotFoundOnRemoveVote() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doThrow(new PostNotFoundException("Post not found with ID: " + postId))
            .when(postVoteService).removeVote(postId, userId);

        mockMvc.perform(delete("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void shouldReturn401WhenRemoveVoteWithoutToken() throws Exception {
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/posts/{postId}/votes", postId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenVotePayloadIsMalformedJson() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/posts/{postId}/votes", postId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{malformed_json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Malformed request payload"))
            .andExpect(jsonPath("$.path").value("/api/v1/posts/" + postId + "/votes"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
