package com.threadly.community;

import com.threadly.community.dto.request.CreateCommunityRequest;
import com.threadly.community.dto.response.CommunityResponse;
import com.threadly.community.entity.CommunityMember;
import com.threadly.community.repository.CommunityMemberRepository;
import com.threadly.community.repository.CommunityRepository;
import com.threadly.community.service.CommunityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class CommunityIntegrationTest {

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityRepository communityRepository;

    @MockitoSpyBean
    private CommunityMemberRepository communityMemberRepository;

    @BeforeEach
    void setUp() {
        communityMemberRepository.deleteAll();
        communityRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        communityMemberRepository.deleteAll();
        communityRepository.deleteAll();
    }

    @Test
    void shouldCreateCommunityAndMemberInDatabase() {
        UUID creatorId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "postgres_club",
            "Postgres Club",
            "A club for Postgres enthusiasts"
        );

        CommunityResponse response = communityService.createCommunity(request, creatorId);

        assertThat(response).isNotNull();
        assertThat(communityRepository.findById(response.id())).isPresent();
        assertThat(communityMemberRepository.existsByCommunityIdAndUserId(response.id(), creatorId)).isTrue();
    }

    @Test
    void shouldRollbackTransactionWhenInitialMembershipCreationFails() {
        UUID creatorId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "rollback_club",
            "Rollback Club",
            "Testing transaction rollback"
        );

        doThrow(new RuntimeException("Simulated membership creation failure"))
            .when(communityMemberRepository).save(any(CommunityMember.class));

        assertThatThrownBy(() -> communityService.createCommunity(request, creatorId))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Simulated membership creation failure");

        assertThat(communityRepository.findByName("rollback_club")).isEmpty();
    }

    @Test
    void shouldGetExistingCommunityWithRealMemberCount() {
        UUID creatorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "count_test",
            "Count Test",
            "Testing real member count"
        );

        CommunityResponse created = communityService.createCommunity(request, creatorId);

        // Add a second member to the community
        communityMemberRepository.save(new CommunityMember(created.id(), otherUserId));

        CommunityResponse fetched = communityService.getCommunity(created.id());

        assertThat(fetched).isNotNull();
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.memberCount()).isEqualTo(2L);
    }

    @Test
    void shouldGetPaginatedCommunitiesOrderedNewestFirst() throws Exception {
        UUID creatorId = UUID.randomUUID();

        CommunityResponse comm1 = communityService.createCommunity(
            new CreateCommunityRequest("first", "First", "First desc"),
            creatorId
        );
        Thread.sleep(20);
        CommunityResponse comm2 = communityService.createCommunity(
            new CreateCommunityRequest("second", "Second", "Second desc"),
            creatorId
        );
        Thread.sleep(20);
        CommunityResponse comm3 = communityService.createCommunity(
            new CreateCommunityRequest("third", "Third", "Third desc"),
            creatorId
        );

        // Page 0, size 2
        var page0 = communityService.getCommunities(0, 2);
        assertThat(page0.totalElements()).isEqualTo(3L);
        assertThat(page0.totalPages()).isEqualTo(2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.last()).isFalse();
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.content().get(0).id()).isEqualTo(comm3.id());
        assertThat(page0.content().get(1).id()).isEqualTo(comm2.id());

        // Page 1, size 2
        var page1 = communityService.getCommunities(1, 2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.last()).isTrue();
        assertThat(page1.content()).hasSize(1);
        assertThat(page1.content().get(0).id()).isEqualTo(comm1.id());
    }

    @Test
    void shouldJoinAndLeaveCommunityIdempotentlyWithRealDatabase() {
        UUID creatorId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        CommunityResponse community = communityService.createCommunity(
            new CreateCommunityRequest("mutation_test", "Mutation Test", "Desc"),
            creatorId
        );
        assertThat(communityService.getCommunity(community.id()).memberCount()).isEqualTo(1L);

        // Member joins
        communityService.joinCommunity(community.id(), memberId);
        assertThat(communityService.getCommunity(community.id()).memberCount()).isEqualTo(2L);
        assertThat(communityMemberRepository.existsByCommunityIdAndUserId(community.id(), memberId)).isTrue();

        // Member joins again (idempotent)
        communityService.joinCommunity(community.id(), memberId);
        assertThat(communityService.getCommunity(community.id()).memberCount()).isEqualTo(2L);

        // Member leaves
        communityService.leaveCommunity(community.id(), memberId);
        assertThat(communityService.getCommunity(community.id()).memberCount()).isEqualTo(1L);
        assertThat(communityMemberRepository.existsByCommunityIdAndUserId(community.id(), memberId)).isFalse();

        // Member leaves again (idempotent)
        communityService.leaveCommunity(community.id(), memberId);
        assertThat(communityService.getCommunity(community.id()).memberCount()).isEqualTo(1L);

        // Creator leaves (allowed)
        communityService.leaveCommunity(community.id(), creatorId);
        assertThat(communityService.getCommunity(community.id()).memberCount()).isEqualTo(0L);
        assertThat(communityMemberRepository.existsByCommunityIdAndUserId(community.id(), creatorId)).isFalse();
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionOnJoinAndLeaveForUnknownCommunity() {
        UUID unknownId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> communityService.joinCommunity(unknownId, userId))
            .isInstanceOf(com.threadly.community.exception.CommunityNotFoundException.class);

        assertThatThrownBy(() -> communityService.leaveCommunity(unknownId, userId))
            .isInstanceOf(com.threadly.community.exception.CommunityNotFoundException.class);
    }
}
