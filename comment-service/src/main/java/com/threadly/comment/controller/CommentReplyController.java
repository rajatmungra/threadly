package com.threadly.comment.controller;

import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.service.CommentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/comments")
public class CommentReplyController {

    private final CommentService commentService;

    public CommentReplyController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/{commentId}/replies")
    public PagedResponse<CommentResponse> getReplies(
        @PathVariable UUID commentId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return commentService.getReplies(commentId, page, size);
    }
}
