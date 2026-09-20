package com.threadly.comment.controller;

import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Comment Replies", description = "Direct replies to comments")
@RestController
@RequestMapping("/api/v1/comments")
public class CommentReplyController {

    private final CommentService commentService;

    public CommentReplyController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "List direct replies for a comment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Replies retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Comment not found")
    })
    @GetMapping("/{commentId}/replies")
    public PagedResponse<CommentResponse> getReplies(
        @PathVariable UUID commentId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return commentService.getReplies(commentId, page, size);
    }
}
