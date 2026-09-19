package com.threadly.post.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PostVoteId implements Serializable {

    private UUID postId;
    private UUID userId;

    public PostVoteId() {
    }

    public PostVoteId(UUID postId, UUID userId) {
        this.postId = postId;
        this.userId = userId;
    }

    public UUID getPostId() {
        return postId;
    }

    public void setPostId(UUID postId) {
        this.postId = postId;
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
        PostVoteId that = (PostVoteId) o;
        return Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, userId);
    }
}
