package com.threadly.comment.repository;

import com.threadly.comment.entity.CommentVote;
import com.threadly.comment.entity.CommentVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentVoteRepository extends JpaRepository<CommentVote, CommentVoteId> {

    Optional<CommentVote> findByCommentIdAndUserId(UUID commentId, UUID userId);

    void deleteByCommentIdAndUserId(UUID commentId, UUID userId);
}
