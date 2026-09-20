package com.threadly.comment.controller;

import com.threadly.comment.dto.request.VoteRequest;
import com.threadly.comment.dto.response.VoteResponse;
import com.threadly.comment.service.CommentVoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Comment Votes", description = "Voting operations on comments")
@RestController
@RequestMapping("/api/v1/comments/{commentId}/votes")
public class CommentVoteController {

    private final CommentVoteService commentVoteService;

    public CommentVoteController(CommentVoteService commentVoteService) {
        this.commentVoteService = commentVoteService;
    }

    @Operation(summary = "Cast or update vote on a comment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vote recorded successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    @PutMapping
    public VoteResponse setVote(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID commentId,
        @Valid @RequestBody VoteRequest request
    ) {
        UUID userId = parseUserId(jwt);
        return commentVoteService.setVote(commentId, userId, request.value());
    }

    @Operation(summary = "Remove vote from a comment")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Vote removed successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Comment not found")
    })
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
