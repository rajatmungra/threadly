package com.threadly.community.service;

import com.threadly.community.dto.request.CreateCommunityRequest;
import com.threadly.community.dto.response.CommunityResponse;
import com.threadly.community.entity.Community;
import com.threadly.community.entity.CommunityMember;
import com.threadly.community.dto.response.PagedResponse;
import com.threadly.community.exception.CommunityNotFoundException;
import com.threadly.community.exception.DuplicateCommunityNameException;
import com.threadly.community.exception.InvalidPaginationException;
import com.threadly.community.repository.CommunityMemberRepository;
import com.threadly.community.repository.CommunityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityRepository communityRepository;

    @Mock
    private CommunityMemberRepository communityMemberRepository;

    @InjectMocks
    private CommunityService communityService;

    @Test
    void shouldCreateCommunityAndMakeCreatorAMember() {
        UUID creatorId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "  SpringBoot  ",
            "  Spring Boot Framework  ",
            "  Discussion about Spring Boot  "
        );

        when(communityRepository.existsByName("springboot")).thenReturn(false);
        when(communityRepository.saveAndFlush(any(Community.class))).thenAnswer(invocation -> {
            Community community = invocation.getArgument(0);
            community.setId(UUID.randomUUID());
            community.setCreatedAt(Instant.now());
            return community;
        });

        CommunityResponse response = communityService.createCommunity(request, creatorId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("springboot");
        assertThat(response.displayName()).isEqualTo("Spring Boot Framework");
        assertThat(response.description()).isEqualTo("Discussion about Spring Boot");
        assertThat(response.createdBy()).isEqualTo(creatorId);
        assertThat(response.memberCount()).isEqualTo(1L);
        assertThat(response.createdAt()).isNotNull();

        ArgumentCaptor<CommunityMember> memberCaptor = ArgumentCaptor.forClass(CommunityMember.class);
        verify(communityMemberRepository).save(memberCaptor.capture());
        CommunityMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getCommunityId()).isEqualTo(response.id());
        assertThat(savedMember.getUserId()).isEqualTo(creatorId);
        assertThat(savedMember.getJoinedAt()).isNotNull();
    }

    @Test
    void shouldThrowDuplicateCommunityNameExceptionWhenNameExistsInPreCheck() {
        UUID creatorId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "springboot",
            "Spring Boot",
            "Description"
        );

        when(communityRepository.existsByName("springboot")).thenReturn(true);

        assertThatThrownBy(() -> communityService.createCommunity(request, creatorId))
            .isInstanceOf(DuplicateCommunityNameException.class)
            .hasMessageContaining("springboot");

        verify(communityRepository, never()).saveAndFlush(any());
        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void shouldThrowDuplicateCommunityNameExceptionWhenDatabaseUniqueConstraintRaceOccurs() {
        UUID creatorId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "springboot",
            "Spring Boot",
            "Description"
        );

        when(communityRepository.existsByName("springboot")).thenReturn(false);
        when(communityRepository.saveAndFlush(any(Community.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_communities_name\""
            ));

        assertThatThrownBy(() -> communityService.createCommunity(request, creatorId))
            .isInstanceOf(DuplicateCommunityNameException.class)
            .hasMessageContaining("springboot");

        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void shouldRethrowDataIntegrityViolationExceptionWhenNotCausedByCommunityNameConstraint() {
        UUID creatorId = UUID.randomUUID();
        CreateCommunityRequest request = new CreateCommunityRequest(
            "springboot",
            "Spring Boot",
            "Description"
        );

        when(communityRepository.existsByName("springboot")).thenReturn(false);
        when(communityRepository.saveAndFlush(any(Community.class)))
            .thenThrow(new DataIntegrityViolationException("null value in column violates not-null constraint"));

        assertThatThrownBy(() -> communityService.createCommunity(request, creatorId))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("not-null");

        verify(communityMemberRepository, never()).save(any());
    }

    @Test
    void shouldReturnCommunityWithRealMemberCountWhenFound() {
        UUID communityId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Community community = new Community("springboot", "Spring Boot", "Description", creatorId);
        community.setId(communityId);
        community.setCreatedAt(Instant.now());

        when(communityRepository.findById(communityId)).thenReturn(Optional.of(community));
        when(communityMemberRepository.countByCommunityId(communityId)).thenReturn(7L);

        CommunityResponse response = communityService.getCommunity(communityId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(communityId);
        assertThat(response.name()).isEqualTo("springboot");
        assertThat(response.displayName()).isEqualTo("Spring Boot");
        assertThat(response.description()).isEqualTo("Description");
        assertThat(response.createdBy()).isEqualTo(creatorId);
        assertThat(response.memberCount()).isEqualTo(7L);
        assertThat(response.createdAt()).isEqualTo(community.getCreatedAt());

        verify(communityRepository).findById(communityId);
        verify(communityMemberRepository).countByCommunityId(communityId);
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionWhenNotFound() {
        UUID communityId = UUID.randomUUID();
        when(communityRepository.findById(communityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> communityService.getCommunity(communityId))
            .isInstanceOf(CommunityNotFoundException.class)
            .hasMessageContaining(communityId.toString());

        verify(communityMemberRepository, never()).countByCommunityId(any());
    }

    @Test
    void shouldReturnPagedCommunitiesSortedNewestFirstAndIdDesc() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        Community comm1 = new Community("comm1", "Comm One", "Desc 1", creatorId);
        comm1.setId(id1);
        comm1.setCreatedAt(Instant.now());

        Community comm2 = new Community("comm2", "Comm Two", "Desc 2", creatorId);
        comm2.setId(id2);
        comm2.setCreatedAt(Instant.now().minusSeconds(60));

        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Community> page = new PageImpl<>(List.of(comm1, comm2), pageable, 2);

        when(communityRepository.findAll(pageable)).thenReturn(page);
        when(communityMemberRepository.countMembersByCommunityIds(List.of(id1, id2)))
            .thenReturn(List.<Object[]>of(new Object[]{id1, 10L}, new Object[]{id2, 3L}));

        PagedResponse<CommunityResponse> response = communityService.getCommunities(0, 20);

        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).name()).isEqualTo("comm1");
        assertThat(response.content().get(0).memberCount()).isEqualTo(10L);
        assertThat(response.content().get(1).name()).isEqualTo("comm2");
        assertThat(response.content().get(1).memberCount()).isEqualTo(3L);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.last()).isTrue();

        verify(communityMemberRepository).countMembersByCommunityIds(List.of(id1, id2));
        verify(communityMemberRepository, never()).countByCommunityId(any());
    }

    @Test
    void shouldReturnEmptyPageWithoutExecutingCountMembersQueryWhenNoCommunitiesFound() {
        Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Community> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(communityRepository.findAll(pageable)).thenReturn(emptyPage);

        PagedResponse<CommunityResponse> response = communityService.getCommunities(1, 10);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(0L);

        verify(communityMemberRepository, never()).countMembersByCommunityIds(any());
        verify(communityMemberRepository, never()).countByCommunityId(any());
    }

    @Test
    void shouldThrowInvalidPaginationExceptionWhenPageIsNegative() {
        assertThatThrownBy(() -> communityService.getCommunities(-1, 20))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("page");
            });

        verify(communityRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldThrowInvalidPaginationExceptionWhenSizeIsLessThanOne() {
        assertThatThrownBy(() -> communityService.getCommunities(0, 0))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("size");
            });

        verify(communityRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldThrowInvalidPaginationExceptionWhenSizeExceedsOneHundred() {
        assertThatThrownBy(() -> communityService.getCommunities(0, 101))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("size");
            });

        verify(communityRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldJoinCommunitySuccessfully() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(communityRepository.existsById(communityId)).thenReturn(true);
        when(communityMemberRepository.insertMemberIfAbsent(eq(communityId), eq(userId), any(Instant.class)))
            .thenReturn(1);

        communityService.joinCommunity(communityId, userId);

        verify(communityRepository).existsById(communityId);
        verify(communityMemberRepository).insertMemberIfAbsent(eq(communityId), eq(userId), any(Instant.class));
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionWhenJoiningUnknownCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(communityRepository.existsById(communityId)).thenReturn(false);

        assertThatThrownBy(() -> communityService.joinCommunity(communityId, userId))
            .isInstanceOf(CommunityNotFoundException.class)
            .hasMessageContaining(communityId.toString());

        verify(communityMemberRepository, never()).insertMemberIfAbsent(any(), any(), any());
    }

    @Test
    void shouldLeaveCommunitySuccessfully() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(communityRepository.existsById(communityId)).thenReturn(true);
        when(communityMemberRepository.deleteByCommunityIdAndUserId(communityId, userId)).thenReturn(1L);

        communityService.leaveCommunity(communityId, userId);

        verify(communityRepository).existsById(communityId);
        verify(communityMemberRepository).deleteByCommunityIdAndUserId(communityId, userId);
    }

    @Test
    void shouldTreatLeaveAsSuccessWhenUserIsNotAMember() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(communityRepository.existsById(communityId)).thenReturn(true);
        when(communityMemberRepository.deleteByCommunityIdAndUserId(communityId, userId)).thenReturn(0L);

        communityService.leaveCommunity(communityId, userId);

        verify(communityRepository).existsById(communityId);
        verify(communityMemberRepository).deleteByCommunityIdAndUserId(communityId, userId);
    }

    @Test
    void shouldAllowCreatorToLeaveCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();

        when(communityRepository.existsById(communityId)).thenReturn(true);
        when(communityMemberRepository.deleteByCommunityIdAndUserId(communityId, creatorId)).thenReturn(1L);

        communityService.leaveCommunity(communityId, creatorId);

        verify(communityRepository).existsById(communityId);
        verify(communityMemberRepository).deleteByCommunityIdAndUserId(communityId, creatorId);
    }

    @Test
    void shouldThrowCommunityNotFoundExceptionWhenLeavingUnknownCommunity() {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(communityRepository.existsById(communityId)).thenReturn(false);

        assertThatThrownBy(() -> communityService.leaveCommunity(communityId, userId))
            .isInstanceOf(CommunityNotFoundException.class)
            .hasMessageContaining(communityId.toString());

        verify(communityMemberRepository, never()).deleteByCommunityIdAndUserId(any(), any());
    }
}
