package com.threadly.community.repository;

import com.threadly.community.entity.CommunityMember;
import com.threadly.community.entity.CommunityMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityMemberRepository extends JpaRepository<CommunityMember, CommunityMemberId> {

    boolean existsByCommunityIdAndUserId(UUID communityId, UUID userId);

    long countByCommunityId(UUID communityId);

    @Query("SELECT cm.communityId, COUNT(cm.userId) FROM CommunityMember cm WHERE cm.communityId IN :communityIds GROUP BY cm.communityId")
    List<Object[]> countMembersByCommunityIds(@Param("communityIds") List<UUID> communityIds);

    @Modifying
    @Query(value = "INSERT INTO community_members (community_id, user_id, joined_at) " +
                   "VALUES (:communityId, :userId, :joinedAt) " +
                   "ON CONFLICT (community_id, user_id) DO NOTHING",
           nativeQuery = true)
    int insertMemberIfAbsent(@Param("communityId") UUID communityId,
                            @Param("userId") UUID userId,
                            @Param("joinedAt") Instant joinedAt);

    long deleteByCommunityIdAndUserId(UUID communityId, UUID userId);
}
