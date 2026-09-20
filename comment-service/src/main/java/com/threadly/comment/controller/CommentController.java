package com.threadly.comment.controller;

import com.threadly.comment.dto.request.CreateCommentRequest;
import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Comments", description = "Post comments management")
@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "Create a comment or reply on a post")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comment created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Post or parent comment not found")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(
        @PathVariable UUID postId,
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        UUID authorId = parseAuthorId(jwt);
        String token = jwt.getTokenValue();
        return commentService.createComment(postId, request, authorId, token);
    }

    @Operation(summary = "List root comments for a post")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Root comments retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public PagedResponse<CommentResponse> getRootComments(
        @PathVariable UUID postId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return commentService.getRootComments(postId, page, size);
    }

    private UUID parseAuthorId(Jwt jwt) {
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
