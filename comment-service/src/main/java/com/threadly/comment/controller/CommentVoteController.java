package com.threadly.comment.controller;

import com.threadly.comment.dto.request.VoteRequest;
import com.threadly.comment.dto.response.VoteResponse;
import com.threadly.comment.service.CommentVoteService;
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
@RequestMapping("/api/v1/comments/{commentId}/votes")
public class CommentVoteController {

    private final CommentVoteService commentVoteService;

    public CommentVoteController(CommentVoteService commentVoteService) {
        this.commentVoteService = commentVoteService;
    }

    @PutMapping
    public VoteResponse setVote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID commentId,
        @Valid @RequestBody VoteRequest request
    ) {
        UUID userId = parseUserId(jwt);
        return commentVoteService.setVote(commentId, userId, request.value());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID commentId
    ) {
        UUID userId = parseUserId(jwt);
        commentVoteService.removeVote(commentId, userId);
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
