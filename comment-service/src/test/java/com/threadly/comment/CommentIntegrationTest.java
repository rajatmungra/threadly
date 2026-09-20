package com.threadly.comment;

import com.threadly.comment.client.PostClient;
import com.threadly.comment.client.PostDto;
import com.threadly.comment.dto.request.CreateCommentRequest;
import com.threadly.comment.dto.response.CommentResponse;
import com.threadly.comment.dto.response.PagedResponse;
import com.threadly.comment.entity.Comment;
import com.threadly.comment.entity.OutboxEvent;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.exception.InvalidParentCommentException;
import com.threadly.comment.repository.CommentRepository;
import com.threadly.comment.repository.OutboxEventRepository;
import com.threadly.comment.service.CommentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class CommentIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @MockitoBean
    private PostClient postClient;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        when(postClient.verifyPostExists(any(), any()))
            .thenAnswer(inv -> new PostDto(inv.getArgument(0), UUID.randomUUID()));
    }

    @AfterEach
    void tearDown() {
        outboxEventRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
    }

    @Test
    void shouldPersistRootCommentInDatabase() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postAuthorId = UUID.randomUUID();
        String token = "jwt.token";

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, postAuthorId));

        CreateCommentRequest request = new CreateCommentRequest(
            "Real PostgreSQL root comment",
            null
        );

        CommentResponse response = commentService.createComment(postId, request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.authorId()).isEqualTo(authorId);
        assertThat(response.parentCommentId()).isNull();
        assertThat(response.content()).isEqualTo("Real PostgreSQL root comment");
        assertThat(response.score()).isEqualTo(0);
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        verify(postClient).verifyPostExists(postId, token);

        Optional<Comment> found = commentRepository.findById(response.id());
        assertThat(found).isPresent();
        Comment comment = found.get();
        assertThat(comment.getPostId()).isEqualTo(postId);
        assertThat(comment.getAuthorId()).isEqualTo(authorId);
        assertThat(comment.getParentCommentId()).isNull();
        assertThat(comment.getContent()).isEqualTo("Real PostgreSQL root comment");
        assertThat(comment.getScore()).isEqualTo(0);

        Optional<OutboxEvent> outboxFound = outboxEventRepository.findById(response.id());
        // eventId is randomly generated UUID, let's verify outbox has 1 row
        assertThat(outboxEventRepository.count()).isEqualTo(1L);
        OutboxEvent outboxEvent = outboxEventRepository.findAll().getFirst();
        assertThat(outboxEvent.getMessageKey()).isEqualTo(postAuthorId.toString());
        assertThat(outboxEvent.getEventType()).isEqualTo("POST_COMMENT");
        assertThat(outboxEvent.getPublishedAt()).isNull();
    }

    @Test
    void shouldPersistReplyCommentWithParentInDatabase() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID replyAuthorId = UUID.randomUUID();
        String token = "jwt.token";

        CommentResponse root = commentService.createComment(
            postId,
            new CreateCommentRequest("Root comment for reply", null),
            authorId,
            token
        );

        CreateCommentRequest replyRequest = new CreateCommentRequest(
            "Reply to root comment",
            root.id()
        );

        CommentResponse reply = commentService.createComment(postId, replyRequest, replyAuthorId, token);

        assertThat(reply).isNotNull();
        assertThat(reply.id()).isNotNull();
        assertThat(reply.postId()).isEqualTo(postId);
        assertThat(reply.authorId()).isEqualTo(replyAuthorId);
        assertThat(reply.parentCommentId()).isEqualTo(root.id());
        assertThat(reply.content()).isEqualTo("Reply to root comment");
        assertThat(reply.score()).isEqualTo(0);

        Optional<Comment> foundReply = commentRepository.findById(reply.id());
        assertThat(foundReply).isPresent();
        assertThat(foundReply.get().getParentCommentId()).isEqualTo(root.id());
        assertThat(foundReply.get().getPostId()).isEqualTo(postId);
    }

    @Test
    void shouldEnforceParentForeignKeyConstraint() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID nonExistentParentId = UUID.randomUUID();

        Comment orphanReply = new Comment(postId, authorId, nonExistentParentId, "Invalid reply with missing parent");

        assertThatThrownBy(() -> commentRepository.saveAndFlush(orphanReply))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectReplyWhenParentBelongsToDifferentPost() {
        UUID postA = UUID.randomUUID();
        UUID postB = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CommentResponse rootOnPostA = commentService.createComment(
            postA,
            new CreateCommentRequest("Root on Post A", null),
            authorId,
            token
        );

        CreateCommentRequest crossPostReply = new CreateCommentRequest(
            "Attempted reply to Post A comment under Post B",
            rootOnPostA.id()
        );

        assertThatThrownBy(() -> commentService.createComment(postB, crossPostReply, authorId, token))
            .isInstanceOf(InvalidParentCommentException.class)
            .hasMessageContaining(postB.toString());

        assertThat(commentRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldFilterRootCommentsAndExcludeReplies() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CommentResponse root1 = commentService.createComment(
            postId, new CreateCommentRequest("Root 1", null), authorId, token
        );
        CommentResponse root2 = commentService.createComment(
            postId, new CreateCommentRequest("Root 2", null), authorId, token
        );
        commentService.createComment(
            postId, new CreateCommentRequest("Reply to Root 1", root1.id()), authorId, token
        );

        PagedResponse<CommentResponse> roots = commentService.getRootComments(postId, 0, 20);

        assertThat(roots.content()).hasSize(2);
        assertThat(roots.totalElements()).isEqualTo(2L);
        assertThat(roots.content().stream().map(CommentResponse::id).toList())
            .containsExactly(root1.id(), root2.id());
        assertThat(roots.content()).allMatch(c -> c.parentCommentId() == null);
    }

    @Test
    void shouldReturnDirectRepliesAndExcludeGrandchildren() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        CommentResponse root = commentService.createComment(
            postId, new CreateCommentRequest("Root comment", null), authorId, token
        );
        CommentResponse reply1 = commentService.createComment(
            postId, new CreateCommentRequest("Direct reply 1", root.id()), authorId, token
        );
        CommentResponse reply2 = commentService.createComment(
            postId, new CreateCommentRequest("Direct reply 2", root.id()), authorId, token
        );
        commentService.createComment(
            postId, new CreateCommentRequest("Nested grandchild reply", reply1.id()), authorId, token
        );

        PagedResponse<CommentResponse> replies = commentService.getReplies(root.id(), 0, 20);

        assertThat(replies.content()).hasSize(2);
        assertThat(replies.totalElements()).isEqualTo(2L);
        assertThat(replies.content().stream().map(CommentResponse::id).toList())
            .containsExactly(reply1.id(), reply2.id());
        assertThat(replies.content()).allMatch(c -> root.id().equals(c.parentCommentId()));
    }

    @Test
    void shouldOrderCommentsChronologicallyWithDeterministicTieBreaker() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant fixedTime = Instant.parse("2026-09-19T10:00:00Z");

        Comment commentA = new Comment(postId, authorId, null, "Comment A");
        commentA.setCreatedAt(fixedTime);
        commentA.setUpdatedAt(fixedTime);
        Comment savedA = commentRepository.saveAndFlush(commentA);

        Comment commentB = new Comment(postId, authorId, null, "Comment B");
        commentB.setCreatedAt(fixedTime);
        commentB.setUpdatedAt(fixedTime);
        Comment savedB = commentRepository.saveAndFlush(commentB);

        Comment commentC = new Comment(postId, authorId, null, "Comment C");
        commentC.setCreatedAt(fixedTime.plusSeconds(30));
        commentC.setUpdatedAt(fixedTime.plusSeconds(30));
        Comment savedC = commentRepository.saveAndFlush(commentC);

        PagedResponse<CommentResponse> response = commentService.getRootComments(postId, 0, 20);

        assertThat(response.content()).hasSize(3);

        UUID tieBreakerFirst = savedA.getId().toString().compareTo(savedB.getId().toString()) < 0 ? savedA.getId() : savedB.getId();
        UUID tieBreakerSecond = savedA.getId().toString().compareTo(savedB.getId().toString()) < 0 ? savedB.getId() : savedA.getId();

        assertThat(response.content().get(0).id()).isEqualTo(tieBreakerFirst);
        assertThat(response.content().get(1).id()).isEqualTo(tieBreakerSecond);
        assertThat(response.content().get(2).id()).isEqualTo(savedC.getId());
    }

    @Test
    void shouldPaginateAcrossPagesCorrectly() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-09-19T10:00:00Z");

        for (int i = 0; i < 5; i++) {
            Comment comment = new Comment(postId, authorId, null, "Comment " + i);
            comment.setCreatedAt(baseTime.plusSeconds(i * 10));
            comment.setUpdatedAt(baseTime.plusSeconds(i * 10));
            commentRepository.save(comment);
        }
        commentRepository.flush();

        PagedResponse<CommentResponse> page0 = commentService.getRootComments(postId, 0, 2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.totalElements()).isEqualTo(5L);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.last()).isFalse();
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.content().get(0).content()).isEqualTo("Comment 0");
        assertThat(page0.content().get(1).content()).isEqualTo("Comment 1");

        PagedResponse<CommentResponse> page1 = commentService.getRootComments(postId, 1, 2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(2);
        assertThat(page1.totalElements()).isEqualTo(5L);
        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page1.last()).isFalse();
        assertThat(page1.content()).hasSize(2);
        assertThat(page1.content().get(0).content()).isEqualTo("Comment 2");
        assertThat(page1.content().get(1).content()).isEqualTo("Comment 3");

        PagedResponse<CommentResponse> page2 = commentService.getRootComments(postId, 2, 2);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.size()).isEqualTo(2);
        assertThat(page2.totalElements()).isEqualTo(5L);
        assertThat(page2.totalPages()).isEqualTo(3);
        assertThat(page2.last()).isTrue();
        assertThat(page2.content()).hasSize(1);
        assertThat(page2.content().get(0).content()).isEqualTo("Comment 4");
    }

    @Test
    void shouldReturnEmptyPageForUnknownPostWithoutErrors() {
        UUID unknownPostId = UUID.randomUUID();

        PagedResponse<CommentResponse> response = commentService.getRootComments(unknownPostId, 0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(0L);
        assertThat(response.totalPages()).isEqualTo(0);
        assertThat(response.last()).isTrue();
    }

    @Test
    void shouldThrowCommentNotFoundExceptionForUnknownCommentInReplies() {
        UUID unknownCommentId = UUID.randomUUID();

        assertThatThrownBy(() -> commentService.getReplies(unknownCommentId, 0, 20))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessageContaining(unknownCommentId.toString());
    }

    @Test
    void shouldPersistOutboxRowForSelfComment() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        String token = "jwt.token";

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, authorId));

        CreateCommentRequest request = new CreateCommentRequest("Self-comment", null);
        CommentResponse response = commentService.createComment(postId, request, authorId, token);

        assertThat(response).isNotNull();
        assertThat(commentRepository.findById(response.id())).isPresent();
        assertThat(outboxEventRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldRollbackCommentWhenOutboxPersistenceFails() {
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID postAuthorId = UUID.randomUUID();
        String token = "jwt.token";

        OutboxEventRepository failingOutboxRepo = mock(OutboxEventRepository.class);
        when(failingOutboxRepo.save(any(OutboxEvent.class)))
            .thenThrow(new DataIntegrityViolationException("Simulated outbox constraint failure"));

        CommentService transactionalService = new CommentService(
            commentRepository,
            failingOutboxRepo,
            postClient,
            transactionTemplate,
            objectMapper,
            "threadly.comment.created.v1"
        );

        when(postClient.verifyPostExists(postId, token)).thenReturn(new PostDto(postId, postAuthorId));

        CreateCommentRequest request = new CreateCommentRequest("Failing comment", null);

        assertThatThrownBy(() -> transactionalService.createComment(postId, request, authorId, token))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(commentRepository.count()).isEqualTo(0L);
        assertThat(outboxEventRepository.count()).isEqualTo(0L);
    }
}
