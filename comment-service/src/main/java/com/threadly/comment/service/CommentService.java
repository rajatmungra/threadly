package com.threadly.comment.service;

import com.threadly.comment.client.PostClient;
import com.threadly.comment.dto.request.CreateCommentRequest;
import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.entity.Comment;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.exception.InvalidPaginationException;
import com.threadly.comment.exception.InvalidParentCommentException;
import com.threadly.comment.repository.CommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostClient postClient;
    private final TransactionTemplate transactionTemplate;

    public CommentService(CommentRepository commentRepository, PostClient postClient, TransactionTemplate transactionTemplate) {
        this.commentRepository = commentRepository;
        this.postClient = postClient;
        this.transactionTemplate = transactionTemplate;
    }

    public CommentResponse createComment(UUID postId, CreateCommentRequest request, UUID authorId, String token) {
        postClient.verifyPostExists(postId, token);

        return transactionTemplate.execute(status -> {
            if (request.parentCommentId() != null) {
                Comment parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException("Comment not found with id: " + request.parentCommentId()));

                if (!parent.getPostId().equals(postId)) {
                    throw new InvalidParentCommentException("Parent comment does not belong to post: " + postId);
                }
            }

            Comment comment = new Comment();
            comment.setPostId(postId);
            comment.setAuthorId(authorId);
            comment.setParentCommentId(request.parentCommentId());
            comment.setContent(request.content().trim());
            comment.setScore(0);
            Instant now = Instant.now();
            comment.setCreatedAt(now);
            comment.setUpdatedAt(now);

            Comment savedComment = commentRepository.save(comment);
            return CommentResponse.fromEntity(savedComment);
        });
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentResponse> getRootComments(UUID postId, int page, int size) {
        validatePagination(page, size);

        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Comment> commentPage = commentRepository.findByPostIdAndParentCommentIdIsNull(postId, pageable);

        List<CommentResponse> content = commentPage.getContent().stream()
            .map(CommentResponse::fromEntity)
            .toList();

        return new PagedResponse<>(
            content,
            commentPage.getNumber(),
            commentPage.getSize(),
            commentPage.getTotalElements(),
            commentPage.getTotalPages(),
            commentPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentResponse> getReplies(UUID commentId, int page, int size) {
        validatePagination(page, size);

        if (!commentRepository.existsById(commentId)) {
            throw new CommentNotFoundException("Comment not found with id: " + commentId);
        }

        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Comment> replyPage = commentRepository.findByParentCommentId(commentId, pageable);

        List<CommentResponse> content = replyPage.getContent().stream()
            .map(CommentResponse::fromEntity)
            .toList();

        return new PagedResponse<>(
            content,
            replyPage.getNumber(),
            replyPage.getSize(),
            replyPage.getTotalElements(),
            replyPage.getTotalPages(),
            replyPage.isLast()
        );
    }

    private void validatePagination(int page, int size) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (page < 0) {
            fieldErrors.put("page", "Page index must not be less than zero");
        }
        if (size < 1) {
            fieldErrors.put("size", "Page size must be at least 1");
        } else if (size > 100) {
            fieldErrors.put("size", "Page size must not exceed 100");
        }
        if (!fieldErrors.isEmpty()) {
            throw new InvalidPaginationException("Invalid pagination parameters", fieldErrors);
        }
    }
}
