package com.threadly.comment;

import com.threadly.comment.dto.response.VoteResponse;
import com.threadly.comment.entity.Comment;
import com.threadly.comment.entity.CommentVote;
import com.threadly.comment.repository.CommentRepository;
import com.threadly.comment.repository.CommentVoteRepository;
import com.threadly.comment.service.CommentVoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CommentVoteIntegrationTest {

    @Autowired
    private CommentVoteService commentVoteService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentVoteRepository commentVoteRepository;

    private Comment testComment;

    @BeforeEach
    void setUp() {
        commentVoteRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();

        Comment comment = new Comment(UUID.randomUUID(), UUID.randomUUID(), null, "Vote Integration Test Comment");
        comment.setScore(0);
        comment.setCreatedAt(Instant.now());
        comment.setUpdatedAt(Instant.now());
        testComment = commentRepository.saveAndFlush(comment);
    }

    @AfterEach
    void tearDown() {
        commentVoteRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
    }

    @Test
    void shouldPersistVoteRowAndIncrementStoredCommentScore() {
        UUID userId = UUID.randomUUID();

        VoteResponse response = commentVoteService.setVote(testComment.getId(), userId, 1);

        assertThat(response.score()).isEqualTo(1);
        assertThat(response.value()).isEqualTo(1);
        assertThat(response.commentId()).isEqualTo(testComment.getId());

        // Verify vote row in database
        Optional<CommentVote> voteOpt = commentVoteRepository.findByCommentIdAndUserId(testComment.getId(), userId);
        assertThat(voteOpt).isPresent();
        CommentVote vote = voteOpt.get();
        assertThat(vote.getValue()).isEqualTo((short) 1);
        assertThat(vote.getCreatedAt()).isNotNull();
        assertThat(vote.getUpdatedAt()).isNotNull();

        // Verify comment score in database
        Comment updatedComment = commentRepository.findById(testComment.getId()).orElseThrow();
        assertThat(updatedComment.getScore()).isEqualTo(1);
    }

    @Test
    void shouldHandleRepeatedVotesWithoutDuplicatingScoreChanges() {
        UUID userId = UUID.randomUUID();

        commentVoteService.setVote(testComment.getId(), userId, 1);
        VoteResponse secondResponse = commentVoteService.setVote(testComment.getId(), userId, 1);

        assertThat(secondResponse.score()).isEqualTo(1);
        assertThat(secondResponse.value()).isEqualTo(1);

        Comment updatedComment = commentRepository.findById(testComment.getId()).orElseThrow();
        assertThat(updatedComment.getScore()).isEqualTo(1);
        assertThat(commentVoteRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldSwitchVoteCorrectlyAndAdjustScoreByTwo() {
        UUID userId = UUID.randomUUID();

        commentVoteService.setVote(testComment.getId(), userId, 1);
        Comment commentAfterUpvote = commentRepository.findById(testComment.getId()).orElseThrow();
        assertThat(commentAfterUpvote.getScore()).isEqualTo(1);

        VoteResponse downvoteResponse = commentVoteService.setVote(testComment.getId(), userId, -1);
        assertThat(downvoteResponse.score()).isEqualTo(-1);
        assertThat(downvoteResponse.value()).isEqualTo(-1);

        Comment commentAfterDownvote = commentRepository.findById(testComment.getId()).orElseThrow();
        assertThat(commentAfterDownvote.getScore()).isEqualTo(-1);

        CommentVote vote = commentVoteRepository.findByCommentIdAndUserId(testComment.getId(), userId).orElseThrow();
        assertThat(vote.getValue()).isEqualTo((short) -1);
    }

    @Test
    void shouldRemoveVoteCorrectlyAndAdjustCommentScore() {
        UUID userId = UUID.randomUUID();

        commentVoteService.setVote(testComment.getId(), userId, 1);
        assertThat(commentRepository.findById(testComment.getId()).orElseThrow().getScore()).isEqualTo(1);

        commentVoteService.removeVote(testComment.getId(), userId);

        assertThat(commentRepository.findById(testComment.getId()).orElseThrow().getScore()).isEqualTo(0);
        assertThat(commentVoteRepository.findByCommentIdAndUserId(testComment.getId(), userId)).isEmpty();

        // Idempotent removal
        commentVoteService.removeVote(testComment.getId(), userId);
        assertThat(commentRepository.findById(testComment.getId()).orElseThrow().getScore()).isEqualTo(0);
    }

    @Test
    void shouldHandleConcurrentVotesOnSameCommentCorrectly() throws Exception {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> errorUser1 = new AtomicReference<>();
        AtomicReference<Throwable> errorUser2 = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                commentVoteService.setVote(testComment.getId(), user1, 1);
            } catch (Throwable t) {
                errorUser1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                commentVoteService.setVote(testComment.getId(), user2, 1);
            } catch (Throwable t) {
                errorUser2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Release both threads simultaneously
        startLatch.countDown();

        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errorUser1.get()).isNull();
        assertThat(errorUser2.get()).isNull();

        // Verify both vote rows are persisted
        assertThat(commentVoteRepository.findByCommentIdAndUserId(testComment.getId(), user1)).isPresent();
        assertThat(commentVoteRepository.findByCommentIdAndUserId(testComment.getId(), user2)).isPresent();
        assertThat(commentVoteRepository.count()).isEqualTo(2L);

        // Verify final score reflects both votes (0 + 1 + 1 = 2)
        Comment finalComment = commentRepository.findById(testComment.getId()).orElseThrow();
        assertThat(finalComment.getScore()).isEqualTo(2);
    }

    @Test
    void shouldCascadeDeleteVotesWhenCommentDeleted() {
        UUID userId = UUID.randomUUID();

        commentVoteService.setVote(testComment.getId(), userId, 1);
        assertThat(commentVoteRepository.count()).isEqualTo(1L);

        commentRepository.delete(testComment);
        commentRepository.flush();

        assertThat(commentVoteRepository.count()).isEqualTo(0L);
    }
}
