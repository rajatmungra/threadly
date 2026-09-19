package com.threadly.post.controller;

import com.threadly.post.dto.request.VoteRequest;
import com.threadly.post.dto.response.VoteResponse;
import com.threadly.post.service.PostVoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts/{postId}/votes")
public class PostVoteController {

    private final PostVoteService postVoteService;

    public PostVoteController(PostVoteService postVoteService) {
        this.postVoteService = postVoteService;
    }

    @PutMapping
    public VoteResponse setVote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID postId,
        @Valid @RequestBody VoteRequest request
    ) {
        UUID userId = parseUserId(jwt);
        return postVoteService.setVote(postId, userId, request.value());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID postId
    ) {
        UUID userId = parseUserId(jwt);
        postVoteService.removeVote(postId, userId);
    }

    private UUID parseUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidBearerTokenException("Missing token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Invalid token subject format");
        }
    }
}
