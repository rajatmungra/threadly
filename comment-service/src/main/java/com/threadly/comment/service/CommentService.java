package com.threadly.comment.service;

import com.threadly.comment.client.PostClient;
import com.threadly.comment.client.PostDto;
import com.threadly.comment.dto.request.CreateCommentRequest;
import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.entity.Comment;
import com.threadly.comment.entity.OutboxEvent;
import com.threadly.comment.event.CommentCreatedEvent;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.exception.InvalidPaginationException;
import com.threadly.comment.exception.InvalidParentCommentException;
import com.threadly.comment.repository.CommentRepository;
import com.threadly.comment.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PostClient postClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final String commentCreatedTopic;

    @org.springframework.beans.factory.annotation.Autowired
    public CommentService(
        CommentRepository commentRepository,
        OutboxEventRepository outboxEventRepository,
        PostClient postClient,
        TransactionTemplate transactionTemplate,
        @Value("${kafka.topics.comment-created:threadly.comment.created.v1}") String commentCreatedTopic
    ) {
        this(commentRepository, outboxEventRepository, postClient, transactionTemplate, JsonMapper.builder().build(), commentCreatedTopic);
    }

    public CommentService(
        CommentRepository commentRepository,
        OutboxEventRepository outboxEventRepository,
        PostClient postClient,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        @Value("${kafka.topics.comment-created:threadly.comment.created.v1}") String commentCreatedTopic
    ) {
        this.commentRepository = commentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.postClient = postClient;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : JsonMapper.builder().build();
        this.commentCreatedTopic = commentCreatedTopic;
    }

    public CommentResponse createComment(UUID postId, CreateCommentRequest request, UUID authorId, String token) {
        PostDto post = postClient.verifyPostExists(postId, token);

        return transactionTemplate.execute(status -> {
            UUID recipientUserId;
            String eventType;

            if (request.parentCommentId() != null) {
                Comment parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException("Comment not found with id: " + request.parentCommentId()));

                if (!parent.getPostId().equals(postId)) {
                    throw new InvalidParentCommentException("Parent comment does not belong to post: " + postId);
                }
                recipientUserId = parent.getAuthorId();
                eventType = CommentCreatedEvent.TYPE_COMMENT_REPLY;
            } else {
                recipientUserId = post.authorId();
                eventType = CommentCreatedEvent.TYPE_POST_COMMENT;
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

            if (!authorId.equals(recipientUserId)) {
                UUID eventId = UUID.randomUUID();
                CommentCreatedEvent event = new CommentCreatedEvent(
                    eventId,
                    savedComment.getId(),
                    postId,
                    savedComment.getParentCommentId(),
                    authorId,
                    recipientUserId,
                    eventType,
                    savedComment.getCreatedAt()
                );

                String payload;
                try {
                    payload = objectMapper.writeValueAsString(event);
                } catch (JacksonException ex) {
                    throw new IllegalStateException("Failed to serialize comment created event", ex);
                }

                OutboxEvent outboxEvent = new OutboxEvent(
                    eventId,
                    commentCreatedTopic,
                    recipientUserId.toString(),
                    eventType,
                    payload,
                    savedComment.getCreatedAt(),
                    null
                );
                outboxEventRepository.save(outboxEvent);
            }

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
