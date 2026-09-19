package com.threadly.comment.service;

import com.threadly.comment.dto.response.VoteResponse;
import com.threadly.comment.entity.Comment;
import com.threadly.comment.entity.CommentVote;
import com.threadly.comment.exception.CommentNotFoundException;
import com.threadly.comment.repository.CommentRepository;
import com.threadly.comment.repository.CommentVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CommentVoteService {

    private final CommentRepository commentRepository;
    private final CommentVoteRepository commentVoteRepository;

    public CommentVoteService(CommentRepository commentRepository, CommentVoteRepository commentVoteRepository) {
        this.commentRepository = commentRepository;
        this.commentVoteRepository = commentVoteRepository;
    }

    @Transactional
    public VoteResponse setVote(UUID commentId, UUID userId, int value) {
        if (value != 1 && value != -1) {
            throw new IllegalArgumentException("Vote value must be 1 or -1");
        }

        Comment comment = commentRepository.findByIdWithLock(commentId)
            .orElseThrow(() -> new CommentNotFoundException("Comment not found with id: " + commentId));

        Optional<CommentVote> existingVoteOpt = commentVoteRepository.findByCommentIdAndUserId(commentId, userId);

        if (existingVoteOpt.isPresent() && existingVoteOpt.get().getValue() == value) {
            return new VoteResponse(commentId, value, comment.getScore());
        }

        int delta;
        if (existingVoteOpt.isEmpty()) {
            CommentVote newVote = new CommentVote(commentId, userId, (short) value);
            commentVoteRepository.save(newVote);
            delta = value;
        } else {
            CommentVote existingVote = existingVoteOpt.get();
            delta = value - existingVote.getValue();
            existingVote.setValue((short) value);
            commentVoteRepository.save(existingVote);
        }

        comment.setScore(comment.getScore() + delta);

        return new VoteResponse(commentId, value, comment.getScore());
    }

    @Transactional
    public void removeVote(UUID commentId, UUID userId) {
        Comment comment = commentRepository.findByIdWithLock(commentId)
            .orElseThrow(() -> new CommentNotFoundException("Comment not found with id: " + commentId));

        Optional<CommentVote> existingVoteOpt = commentVoteRepository.findByCommentIdAndUserId(commentId, userId);

        if (existingVoteOpt.isPresent()) {
            CommentVote existingVote = existingVoteOpt.get();
            int delta = -existingVote.getValue();
            commentVoteRepository.delete(existingVote);
            comment.setScore(comment.getScore() + delta);
        }
    }
}
