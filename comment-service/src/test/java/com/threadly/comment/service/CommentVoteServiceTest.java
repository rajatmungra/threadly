package com.threadly.comment.service;

import com.threadly.comment.dto.response.VoteResponse;
import com.threadly.comment.entity.Comment;
import com.threadly.comment.entity.CommentVote;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.repository.CommentRepository;
import com.threadly.comment.repository.CommentVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentVoteServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentVoteRepository commentVoteRepository;

    @InjectMocks
    private CommentVoteService commentVoteService;

    private UUID commentId;
    private UUID userId;
    private Comment comment;

    @BeforeEach
    void setUp() {
        commentId = UUID.randomUUID();
        userId = UUID.randomUUID();
        comment = new Comment(UUID.randomUUID(), UUID.randomUUID(), null, "Test comment");
        comment.setId(commentId);
        comment.setScore(0);
    }

    @Test
    void shouldUpvoteWhenNoExistingVote() {
        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.empty());

        VoteResponse response = commentVoteService.setVote(commentId, userId, 1);

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(1);
        assertThat(comment.getScore()).isEqualTo(1);
        verify(commentVoteRepository).save(any(CommentVote.class));
    }

    @Test
    void shouldDownvoteWhenNoExistingVote() {
        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.empty());

        VoteResponse response = commentVoteService.setVote(commentId, userId, -1);

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.value()).isEqualTo(-1);
        assertThat(response.score()).isEqualTo(-1);
        assertThat(comment.getScore()).isEqualTo(-1);
        verify(commentVoteRepository).save(any(CommentVote.class));
    }

    @Test
    void shouldReturnEarlyWhenRepeatedIdenticalVote() {
        comment.setScore(1);
        CommentVote existingVote = new CommentVote(commentId, userId, (short) 1);

        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.of(existingVote));

        VoteResponse response = commentVoteService.setVote(commentId, userId, 1);

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(1);
        assertThat(comment.getScore()).isEqualTo(1);
        verify(commentVoteRepository, never()).save(any(CommentVote.class));
    }

    @Test
    void shouldSwitchVoteFromUpvoteToDownvote() {
        comment.setScore(1);
        CommentVote existingVote = new CommentVote(commentId, userId, (short) 1);

        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.of(existingVote));

        VoteResponse response = commentVoteService.setVote(commentId, userId, -1);

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.value()).isEqualTo(-1);
        assertThat(response.score()).isEqualTo(-1);
        assertThat(comment.getScore()).isEqualTo(-1);
        assertThat(existingVote.getValue()).isEqualTo((short) -1);
        verify(commentVoteRepository).save(existingVote);
    }

    @Test
    void shouldSwitchVoteFromDownvoteToUpvote() {
        comment.setScore(-1);
        CommentVote existingVote = new CommentVote(commentId, userId, (short) -1);

        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.of(existingVote));

        VoteResponse response = commentVoteService.setVote(commentId, userId, 1);

        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(1);
        assertThat(comment.getScore()).isEqualTo(1);
        assertThat(existingVote.getValue()).isEqualTo((short) 1);
        verify(commentVoteRepository).save(existingVote);
    }

    @Test
    void shouldThrowExceptionWhenSetVoteForNonExistentComment() {
        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentVoteService.setVote(commentId, userId, 1))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessageContaining("Comment not found with id: " + commentId);
    }

    @Test
    void shouldThrowExceptionWhenSetVoteWithInvalidValue() {
        assertThatThrownBy(() -> commentVoteService.setVote(commentId, userId, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vote value must be 1 or -1");

        assertThatThrownBy(() -> commentVoteService.setVote(commentId, userId, 2))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Vote value must be 1 or -1");
    }

    @Test
    void shouldRemoveExistingUpvote() {
        comment.setScore(1);
        CommentVote existingVote = new CommentVote(commentId, userId, (short) 1);

        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.of(existingVote));

        commentVoteService.removeVote(commentId, userId);

        assertThat(comment.getScore()).isEqualTo(0);
        verify(commentVoteRepository).delete(existingVote);
    }

    @Test
    void shouldRemoveExistingDownvote() {
        comment.setScore(-1);
        CommentVote existingVote = new CommentVote(commentId, userId, (short) -1);

        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.of(existingVote));

        commentVoteService.removeVote(commentId, userId);

        assertThat(comment.getScore()).isEqualTo(0);
        verify(commentVoteRepository).delete(existingVote);
    }

    @Test
    void shouldNoOpWhenRemovingNonExistentVote() {
        comment.setScore(1);

        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.of(comment));
        when(commentVoteRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.empty());

        commentVoteService.removeVote(commentId, userId);

        assertThat(comment.getScore()).isEqualTo(1);
        verify(commentVoteRepository, never()).delete(any(CommentVote.class));
    }

    @Test
    void shouldThrowExceptionWhenRemoveVoteForNonExistentComment() {
        when(commentRepository.findByIdWithLock(commentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentVoteService.removeVote(commentId, userId))
            .isInstanceOf(CommentNotFoundException.class)
            .hasMessageContaining("Comment not found with id: " + commentId);
    }
}
