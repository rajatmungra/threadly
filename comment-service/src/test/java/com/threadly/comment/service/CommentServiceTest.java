package com.threadly.comment.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
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
import com.threadly.comment.exception.PostNotFoundException;
import com.threadly.comment.exception.PostServiceUnavailableException;
import com.threadly.comment.repository.CommentRepository;
import com.threadly.comment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PostClient postClient;

    private ObjectMapper objectMapper;
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        objectMapper = JsonMapper.builder().build();
        commentService = new CommentService(
            commentRepository,
            outboxEventRepository,
            postClient,
            transactionTemplate,
            objectMapper,
            "threadly.comment.created.v1"
        );
    }

    @Test
    void shouldCreateRootCommentSuccessfully() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postAuthorId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest(
            "   This is a root comment with whitespace   ",
            null
        );

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, postAuthorId));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        CommentResponse response = commentService.createComment(postId, request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.parentCommentId()).isNull();
        assertThat(response.content()).isEqualTo("This is a root comment with whitespace");
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        InOrder inOrder = Mockito.inOrder(postClient, commentRepository, outboxEventRepository);
        inOrder.verify(postClient).verifyPostExists(postId, token);
        inOrder.verify(commentRepository).save(any(Comment.class));
        inOrder.verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldCreateOutboxEventForRootCommentWithCorrectValues() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postAuthorId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest("Root comment", null);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, postAuthorId));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        commentService.createComment(postId, request, authorId, token);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.getTopic()).isEqualTo("threadly.comment.created.v1");
        assertThat(outboxEvent.getMessageKey()).isEqualTo(postAuthorId.toString());
        assertThat(outboxEvent.getEventType()).isEqualTo(CommentCreatedEvent.TYPE_POST_COMMENT);
        assertThat(outboxEvent.getPublishedAt()).isNull();

        CommentCreatedEvent event = objectMapper.readValue(outboxEvent.getPayload(), CommentCreatedEvent.class);
        assertThat(event.eventId()).isEqualTo(outboxEvent.getId());
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.actorUserId()).isEqualTo(authorId);
        assertThat(event.recipientUserId()).isEqualTo(postAuthorId);
        assertThat(event.type()).isEqualTo("POST_COMMENT");
        assertThat(event.parentCommentId()).isNull();
    }

    @Test
    void shouldCreateReplySuccessfully() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID parentAuthorId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        String token = "jwt.token";

        Comment parent = new Comment(postId, parentAuthorId, null, "Parent content");
        parent.setId(parentCommentId);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, UUID.randomUUID()));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        CreateCommentRequest request = new CreateCommentRequest(
            "This is a reply comment",
            parentCommentId
        );

        CommentResponse response = commentService.createComment(postId, request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.parentCommentId()).isEqualTo(parentCommentId);
        assertThat(response.content()).isEqualTo("This is a reply comment");
        assertThat(response.score()).isEqualTo(0);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        Comment savedComment = captor.getValue();
        assertThat(savedComment.getParentCommentId()).isEqualTo(parentCommentId);
        assertThat(savedComment.getPostId()).isEqualTo(postId);

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldCreateOutboxEventForReplyWithCorrectValues() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID parentAuthorId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        String token = "jwt.token";

        Comment parent = new Comment(postId, parentAuthorId, null, "Parent content");
        parent.setId(parentCommentId);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, UUID.randomUUID()));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        CreateCommentRequest request = new CreateCommentRequest("Reply content", parentCommentId);
        commentService.createComment(postId, request, authorId, token);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getMessageKey()).isEqualTo(parentAuthorId.toString());
        assertThat(outboxEvent.getEventType()).isEqualTo(CommentCreatedEvent.TYPE_COMMENT_REPLY);

        CommentCreatedEvent event = objectMapper.readValue(outboxEvent.getPayload(), CommentCreatedEvent.class);
        assertThat(event.eventId()).isEqualTo(outboxEvent.getId());
        assertThat(event.recipientUserId()).isEqualTo(parentAuthorId);
        assertThat(event.type()).isEqualTo("COMMENT_REPLY");
        assertThat(event.parentCommentId()).isEqualTo(parentCommentId);
    }

    @Test
    void shouldCreateOutboxEventWhenUserCommentsOnOwnPost() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest("Self-comment", null);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, authorId));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        CommentResponse response = commentService.createComment(postId, request, authorId, token);

        assertThat(response).isNotNull();
        verify(commentRepository).save(any(Comment.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.getMessageKey()).isEqualTo(authorId.toString());
        assertThat(outboxEvent.getEventType()).isEqualTo(CommentCreatedEvent.TYPE_POST_COMMENT);

        CommentCreatedEvent event = objectMapper.readValue(outboxEvent.getPayload(), CommentCreatedEvent.class);
        assertThat(event.actorUserId()).isEqualTo(authorId);
        assertThat(event.recipientUserId()).isEqualTo(authorId);
    }

    @Test
    void shouldCreateOutboxEventWhenUserRepliesToOwnComment() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        String token = "jwt.token";

        Comment parent = new Comment(postId, authorId, null, "My own parent comment");
        parent.setId(parentCommentId);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, UUID.randomUUID()));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            comment.setCreatedAt(Instant.now());
            comment.setUpdatedAt(Instant.now());
            return comment;
        });

        CreateCommentRequest request = new CreateCommentRequest("Self-reply", parentCommentId);
        CommentResponse response = commentService.createComment(postId, request, authorId, token);

        assertThat(response).isNotNull();
        verify(commentRepository).save(any(Comment.class));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent).isNotNull();
        assertThat(outboxEvent.getMessageKey()).isEqualTo(authorId.toString());
        assertThat(outboxEvent.getEventType()).isEqualTo(CommentCreatedEvent.TYPE_COMMENT_REPLY);

        CommentCreatedEvent event = objectMapper.readValue(outboxEvent.getPayload(), CommentCreatedEvent.class);
        assertThat(event.actorUserId()).isEqualTo(authorId);
        assertThat(event.recipientUserId()).isEqualTo(authorId);
        assertThat(event.parentCommentId()).isEqualTo(parentCommentId);
    }

    @Test
    void shouldRollbackWhenOutboxSerializationFails() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postAuthorId = UUID.randomUUID();
        String token = "jwt.token";

        ObjectMapper failingMapper = Mockito.mock(ObjectMapper.class);
        JacksonException jacksonException = Mockito.mock(JacksonException.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(jacksonException);

        PlatformTransactionManager tm = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override
            public void commit(TransactionStatus status) {}
            @Override
            public void rollback(TransactionStatus status) {}
        };

        CommentService serviceWithFailingMapper = new CommentService(
            commentRepository,
            outboxEventRepository,
            postClient,
            new TransactionTemplate(tm),
            failingMapper,
            "threadly.comment.created.v1"
        );

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, postAuthorId));
        when(commentRepository.save(any(Comment.class))).thenReturn(new Comment());

        CreateCommentRequest request = new CreateCommentRequest("Comment", null);

        assertThatThrownBy(() -> serviceWithFailingMapper.createComment(postId, request, authorId, token))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to serialize comment created event");

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void shouldVerifyPostServiceBeforePersistence() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, UUID.randomUUID()));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            return comment;
        });

        commentService.createComment(postId, request, authorId, token);

        InOrder inOrder = Mockito.inOrder(postClient, commentRepository);
        inOrder.verify(postClient).verifyPostExists(postId, token);
        inOrder.verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void shouldNotPersistCommentWhenPostNotFound() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

        Mockito.doThrow(new PostNotFoundException("Post not found with id: " + postId))
            .when(postClient).verifyPostExists(postId, token);

        assertThatThrownBy(() -> commentService.createComment(postId, request, authorId, token))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessageContaining(postId.toString());

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void shouldNotPersistCommentWhenPostServiceUnavailable() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

        Mockito.doThrow(new PostServiceUnavailableException("Post service is unavailable"))
            .when(postClient).verifyPostExists(postId, token);

        assertThatThrownBy(() -> commentService.createComment(postId, request, authorId, token))
            .isInstanceOf(PostServiceUnavailableException.class)
            .hasMessageContaining("Post service is unavailable");

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void shouldThrowCommentNotFoundExceptionWhenParentCommentDoesNotExist() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID nonExistentParentId = UUID.randomUUID();
        String token = "jwt.token";

        CreateCommentRequest request = new CreateCommentRequest("Reply content", nonExistentParentId);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, UUID.randomUUID()));
        when(commentRepository.findById(nonExistentParentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(postId, request, authorId, token))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessageContaining(nonExistentParentId.toString());

        verify(postClient).verifyPostExists(postId, token);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void shouldThrowInvalidParentCommentExceptionWhenParentBelongsToDifferentPost() {
        UUID postId = UUID.randomUUID();
        UUID differentPostId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        String token = "jwt.token";

        Comment parent = new Comment(differentPostId, UUID.randomUUID(), null, "Parent on another post");
        parent.setId(parentCommentId);

        CreateCommentRequest request = new CreateCommentRequest("Reply content", parentCommentId);

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, UUID.randomUUID()));
        when(commentRepository.findById(parentCommentId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(postId, request, authorId, token))
            .isInstanceOf(InvalidParentCommentException.class)
            .hasMessageContaining("Parent comment does not belong to post: " + postId);

        verify(postClient).verifyPostExists(postId, token);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void shouldGetRootCommentsWithDeterministicSort() {
        UUID postId = UUID.randomUUID();
        Comment comment = new Comment(postId, UUID.randomUUID(), null, "Root comment");
        comment.setId(UUID.randomUUID());
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());

        Page<Comment> page = new PageImpl<>(List.of(comment), PageRequest.of(0, 20), 1);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(commentRepository.findByPostIdAndParentCommentIdIsNull(eq(postId), pageableCaptor.capture()))
            .thenReturn(page);

        PagedResponse<CommentResponse> response = commentService.getRootComments(postId, 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().content()).isEqualTo("Root comment");
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.last()).isTrue();

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSort()).isEqualTo(
            Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        );

        verify(postClient, never()).verifyPostExists(any(), any());
    }

    @Test
    void shouldReturnEmptyPageWhenPostHasNoComments() {
        UUID postId = UUID.randomUUID();
        Page<Comment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(commentRepository.findByPostIdAndParentCommentIdIsNull(eq(postId), any(Pageable.class)))
            .thenReturn(emptyPage);

        PagedResponse<CommentResponse> response = commentService.getRootComments(postId, 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(0L);
        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.last()).isTrue();

        verify(postClient, never()).verifyPostExists(any(), any());
    }

    @Test
    void shouldThrowInvalidPaginationExceptionForRootComments() {
        UUID postId = UUID.randomUUID();

        assertThatThrownBy(() -> commentService.getRootComments(postId, -1, 20))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> assertThat(((InvalidPaginationException) ex).getFieldErrors()).containsKey("page"));

        assertThatThrownBy(() -> commentService.getRootComments(postId, 0, 0))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> assertThat(((InvalidPaginationException) ex).getFieldErrors()).containsKey("size"));

        assertThatThrownBy(() -> commentService.getRootComments(postId, 0, 101))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> assertThat(((InvalidPaginationException) ex).getFieldErrors()).containsKey("size"));

        verify(commentRepository, never()).findByPostIdAndParentCommentIdIsNull(any(), any());
    }

    @Test
    void shouldGetRepliesWithDeterministicSort() {
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Comment reply = new Comment(postId, UUID.randomUUID(), commentId, "Direct reply");
        reply.setId(UUID.randomUUID());
        reply.setCreatedAt(Instant.now());
        reply.setUpdatedAt(Instant.now());

        when(commentRepository.existsById(commentId)).thenReturn(true);

        Page<Comment> page = new PageImpl<>(List.of(reply), PageRequest.of(0, 20), 1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(commentRepository.findByParentCommentId(eq(commentId), pageableCaptor.capture()))
            .thenReturn(page);

        PagedResponse<CommentResponse> response = commentService.getReplies(commentId, 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().content()).isEqualTo("Direct reply");
        assertThat(response.content().getFirst().parentCommentId()).isEqualTo(commentId);

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getSort()).isEqualTo(
            Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
        );

        verify(postClient, never()).verifyPostExists(any(), any());
    }

    @Test
    void shouldThrowCommentNotFoundExceptionWhenParentCommentDoesNotExistInGetReplies() {
        UUID nonExistentCommentId = UUID.randomUUID();

        when(commentRepository.existsById(nonExistentCommentId)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getReplies(nonExistentCommentId, 0, 20))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessageContaining(nonExistentCommentId.toString());

        verify(commentRepository, never()).findByParentCommentId(any(), any());
        verify(postClient, never()).verifyPostExists(any(), any());
    }

    @Test
    void shouldThrowInvalidPaginationExceptionForReplies() {
        UUID commentId = UUID.randomUUID();

        assertThatThrownBy(() -> commentService.getReplies(commentId, -1, 20))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> assertThat(((InvalidPaginationException) ex).getFieldErrors()).containsKey("page"));

        assertThatThrownBy(() -> commentService.getReplies(commentId, 0, 0))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> assertThat(((InvalidPaginationException) ex).getFieldErrors()).containsKey("size"));

        assertThatThrownBy(() -> commentService.getReplies(commentId, 0, 101))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> assertThat(((InvalidPaginationException) ex).getFieldErrors()).containsKey("size"));

        verify(commentRepository, never()).findByParentCommentId(any(), any());
    }
}
