package com.threadly.post.service;

import com.threadly.post.cache.PostFeedCache;
import com.threadly.post.dto.response.VoteResponse;
import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostVote;
import com.threadly.post.exception.PostNotFoundException;
import com.threadly.post.repository.PostRepository;
import com.threadly.post.repository.PostVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PostVoteService {

    private final PostRepository postRepository;
    private final PostVoteRepository postVoteRepository;
    private final PostFeedCache postFeedCache;

    public PostVoteService(PostRepository postRepository, PostVoteRepository postVoteRepository, PostFeedCache postFeedCache) {
        this.postRepository = postRepository;
        this.postVoteRepository = postVoteRepository;
        this.postFeedCache = postFeedCache;
    }

    @Transactional
    public VoteResponse setVote(UUID postId, UUID userId, int value) {
        if (value != 1 && value != -1) {
            throw new IllegalArgumentException("Vote value must be 1 or -1");
        }

        Post post = postRepository.findByIdWithLock(postId)
            .orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));

        Optional<PostVote> existingVoteOpt = postVoteRepository.findByPostIdAndUserId(postId, userId);

        int delta;
        if (existingVoteOpt.isEmpty()) {
            PostVote newVote = new PostVote(postId, userId, (short) value);
            postVoteRepository.save(newVote);
            delta = value;
        } else {
            PostVote existingVote = existingVoteOpt.get();
            if (existingVote.getValue() != value) {
                delta = value - existingVote.getValue();
                existingVote.setValue((short) value);
                postVoteRepository.save(existingVote);
            } else {
                return new VoteResponse(postId, value, post.getScore());
            }
        }

        if (delta != 0) {
            post.setScore(post.getScore() + delta);
            postFeedCache.evictAfterCommit(post.getCommunityId());
        }

        return new VoteResponse(postId, value, post.getScore());
    }

    @Transactional
    public void removeVote(UUID postId, UUID userId) {
        Post post = postRepository.findByIdWithLock(postId)
            .orElseThrow(() -> new PostNotFoundException("Post not found with ID: " + postId));

        Optional<PostVote> existingVoteOpt = postVoteRepository.findByPostIdAndUserId(postId, userId);

        if (existingVoteOpt.isPresent()) {
            PostVote existingVote = existingVoteOpt.get();
            int delta = -existingVote.getValue();
            postVoteRepository.delete(existingVote);
            post.setScore(post.getScore() + delta);
            postFeedCache.evictAfterCommit(post.getCommunityId());
        }
    }
}
