package com.threadly.post.service;

import com.threadly.post.cache.PostFeedCache;
import com.threadly.post.dto.response.VoteResponse;
import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostType;
import com.threadly.post.entity.PostVote;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.repository.PostRepository;
import com.threadly.post.repository.PostVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostVoteServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostVoteRepository postVoteRepository;

    @Mock
    private PostFeedCache postFeedCache;

    @InjectMocks
    private PostVoteService postVoteService;

    private UUID postId;
    private UUID userId;
    private Post post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();
        post = new Post();
        post.setId(postId);
        post.setCommunityId(UUID.randomUUID());
        post.setAuthorId(UUID.randomUUID());
        post.setTitle("Vote Test Post");
        post.setType(PostType.TEXT);
        post.setScore(0);
        post.setCreatedAt(Instant.now());
        post.setUpdatedAt(Instant.now());
    }

    @Test
    void shouldCreateUpvoteAndIncrementScoreByOne() {
        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

        VoteResponse response = postVoteService.setVote(postId, userId, 1);

        assertThat(response).isNotNull();
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(1);
        assertThat(post.getScore()).isEqualTo(1);

        ArgumentCaptor<PostVote> voteCaptor = ArgumentCaptor.forClass(PostVote.class);
        verify(postVoteRepository).save(voteCaptor.capture());
        PostVote savedVote = voteCaptor.getValue();
        assertThat(savedVote.getPostId()).isEqualTo(postId);
        assertThat(savedVote.getUserId()).isEqualTo(userId);
        assertThat(savedVote.getValue()).isEqualTo((short) 1);
        verify(postFeedCache).evictAfterCommit(post.getCommunityId());
    }

    @Test
    void shouldCreateDownvoteAndDecrementScoreByOne() {
        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

        VoteResponse response = postVoteService.setVote(postId, userId, -1);

        assertThat(response).isNotNull();
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.value()).isEqualTo(-1);
        assertThat(response.score()).isEqualTo(-1);
        assertThat(post.getScore()).isEqualTo(-1);

        ArgumentCaptor<PostVote> voteCaptor = ArgumentCaptor.forClass(PostVote.class);
        verify(postVoteRepository).save(voteCaptor.capture());
        PostVote savedVote = voteCaptor.getValue();
        assertThat(savedVote.getValue()).isEqualTo((short) -1);
        verify(postFeedCache).evictAfterCommit(post.getCommunityId());
    }

    @Test
    void shouldSwitchUpvoteToDownvoteWithDeltaMinusTwo() {
        post.setScore(1);
        PostVote existingVote = new PostVote(postId, userId, (short) 1);

        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existingVote));

        VoteResponse response = postVoteService.setVote(postId, userId, -1);

        assertThat(response.value()).isEqualTo(-1);
        assertThat(response.score()).isEqualTo(-1);
        assertThat(post.getScore()).isEqualTo(-1);
        assertThat(existingVote.getValue()).isEqualTo((short) -1);
        verify(postVoteRepository).save(existingVote);
        verify(postFeedCache).evictAfterCommit(post.getCommunityId());
    }

    @Test
    void shouldSwitchDownvoteToUpvoteWithDeltaPlusTwo() {
        post.setScore(-1);
        PostVote existingVote = new PostVote(postId, userId, (short) -1);

        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existingVote));

        VoteResponse response = postVoteService.setVote(postId, userId, 1);

        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(1);
        assertThat(post.getScore()).isEqualTo(1);
        assertThat(existingVote.getValue()).isEqualTo((short) 1);
        verify(postVoteRepository).save(existingVote);
        verify(postFeedCache).evictAfterCommit(post.getCommunityId());
    }

    @Test
    void shouldNotChangeScoreWhenSubmittingSameVoteTwice() {
        post.setScore(1);
        PostVote existingVote = new PostVote(postId, userId, (short) 1);

        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existingVote));

        VoteResponse response = postVoteService.setVote(postId, userId, 1);

        assertThat(response.value()).isEqualTo(1);
        assertThat(response.score()).isEqualTo(1);
        assertThat(post.getScore()).isEqualTo(1);
        verify(postVoteRepository, never()).save(any());
        verify(postFeedCache, never()).evictAfterCommit(any());
    }

    @Test
    void shouldRemoveUpvoteAndDecrementScore() {
        post.setScore(5);
        PostVote existingVote = new PostVote(postId, userId, (short) 1);

        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existingVote));

        postVoteService.removeVote(postId, userId);

        assertThat(post.getScore()).isEqualTo(4);
        verify(postVoteRepository).delete(existingVote);
        verify(postFeedCache).evictAfterCommit(post.getCommunityId());
    }

    @Test
    void shouldRemoveDownvoteAndIncrementScore() {
        post.setScore(5);
        PostVote existingVote = new PostVote(postId, userId, (short) -1);

        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existingVote));

        postVoteService.removeVote(postId, userId);

        assertThat(post.getScore()).isEqualTo(6);
        verify(postVoteRepository).delete(existingVote);
        verify(postFeedCache).evictAfterCommit(post.getCommunityId());
    }

    @Test
    void shouldBeIdempotentWhenRemovingNonExistentVote() {
        post.setScore(5);

        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.of(post));
        when(postVoteRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

        postVoteService.removeVote(postId, userId);

        assertThat(post.getScore()).isEqualTo(5);
        verify(postVoteRepository, never()).delete(any());
        verify(postFeedCache, never()).evictAfterCommit(any());
    }

    @Test
    void shouldThrowPostNotFoundExceptionWhenSettingVoteOnMissingPost() {
        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postVoteService.setVote(postId, userId, 1))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessageContaining(postId.toString());

        verify(postVoteRepository, never()).save(any());
    }

    @Test
    void shouldThrowPostNotFoundExceptionWhenRemovingVoteOnMissingPost() {
        when(postRepository.findByIdWithLock(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postVoteService.removeVote(postId, userId))
            .isInstanceOf(PostNotFoundException.class)
            .hasMessageContaining(postId.toString());

        verify(postVoteRepository, never()).delete(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenVoteValueIsInvalid() {
        assertThatThrownBy(() -> postVoteService.setVote(postId, userId, 0))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> postVoteService.setVote(postId, userId, 2))
            .isInstanceOf(IllegalArgumentException.class);

        verify(postRepository, never()).findByIdWithLock(any());
    }
}
