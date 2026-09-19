package com.threadly.community.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class CommunityMemberId implements Serializable {

    private UUID communityId;
    private UUID userId;

    public CommunityMemberId() {
    }

    public CommunityMemberId(UUID communityId, UUID userId) {
        this.communityId = communityId;
        this.userId = userId;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public void setCommunityId(UUID communityId) {
        this.communityId = communityId;
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
        CommunityMemberId that = (CommunityMemberId) o;
        return Objects.equals(communityId, that.communityId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(communityId, userId);
    }
}
