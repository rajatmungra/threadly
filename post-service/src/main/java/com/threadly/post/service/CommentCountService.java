package com.threadly.post.service;

import com.threadly.post.cache.PostFeedCache;
import com.threadly.post.event.CommentCreatedEvent;
import com.threadly.post.repository.PostRepository;
import com.threadly.post.repository.ProcessedCommentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommentCountService {

    private static final Logger log = LoggerFactory.getLogger(CommentCountService.class);

    private final ProcessedCommentEventRepository processedCommentEventRepository;
    private final PostRepository postRepository;
    private final PostFeedCache postFeedCache;

    public CommentCountService(
            ProcessedCommentEventRepository processedCommentEventRepository,
            PostRepository postRepository,
            PostFeedCache postFeedCache
    ) {
        this.processedCommentEventRepository = processedCommentEventRepository;
        this.postRepository = postRepository;
        this.postFeedCache = postFeedCache;
    }

    @Transactional
    public void processCommentCreated(CommentCreatedEvent event) {
        Instant processedAt = event.createdAt() != null ? event.createdAt() : Instant.now();
        int inserted = processedCommentEventRepository.insertIfNotExists(event.eventId(), event.postId(), processedAt);
        if (inserted == 0) {
            log.info("Duplicate comment created event ignored: eventId={}, postId={}", event.eventId(), event.postId());
            return;
        }

        Optional<UUID> communityIdOpt = postRepository.findCommunityIdByPostId(event.postId());
        if (communityIdOpt.isEmpty()) {
            log.warn("Post not found when incrementing comment count: postId={}, eventId={}", event.postId(), event.eventId());
            return;
        }

        postRepository.incrementCommentCount(event.postId());
        postFeedCache.evictAfterCommit(communityIdOpt.get());
        log.info("Incremented comment count for post: postId={}, eventId={}", event.postId(), event.eventId());
    }
}
