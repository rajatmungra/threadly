package com.threadly.comment.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CommentVoteId implements Serializable {

    private UUID commentId;
    private UUID userId;

    public CommentVoteId() {
    }

    public CommentVoteId(UUID commentId, UUID userId) {
        this.commentId = commentId;
        this.userId = userId;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public void setCommentId(UUID commentId) {
        this.commentId = commentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommentVoteId that = (CommentVoteId) o;
        return Objects.equals(commentId, that.commentId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commentId, userId);
    }
}
