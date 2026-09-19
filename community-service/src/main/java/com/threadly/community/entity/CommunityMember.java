package com.threadly.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "community_members")
@IdClass(CommunityMemberId.class)
public class CommunityMember {

    @Id
    @Column(name = "community_id", nullable = false, updatable = false)
    private UUID communityId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    public CommunityMember() {
    }

    public CommunityMember(UUID communityId, UUID userId) {
        this.communityId = communityId;
        this.userId = userId;
    }

    public CommunityMember(UUID communityId, UUID userId, Instant joinedAt) {
        this.communityId = communityId;
        this.userId = userId;
        this.joinedAt = joinedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
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

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommunityMember that = (CommunityMember) o;
        return Objects.equals(communityId, that.communityId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(communityId, userId);
    }
}
