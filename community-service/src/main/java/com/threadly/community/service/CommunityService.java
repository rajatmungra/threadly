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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository communityMemberRepository;

    public CommunityService(
        CommunityRepository communityRepository,
        CommunityMemberRepository communityMemberRepository
    ) {
        this.communityRepository = communityRepository;
        this.communityMemberRepository = communityMemberRepository;
    }

    @Transactional
    public CommunityResponse createCommunity(CreateCommunityRequest request, UUID creatorUserId) {
        String normalizedName = request.name().trim().toLowerCase(Locale.ROOT);
        String normalizedDisplayName = request.displayName().trim();
        String normalizedDescription = request.description() != null ? request.description().trim() : null;

        if (communityRepository.existsByName(normalizedName)) {
            throw new DuplicateCommunityNameException("Community with name '" + normalizedName + "' already exists");
        }

        Community community = new Community(
            normalizedName,
            normalizedDisplayName,
            normalizedDescription,
            creatorUserId
        );

        try {
            community = communityRepository.saveAndFlush(community);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueNameViolation(ex)) {
                throw new DuplicateCommunityNameException("Community with name '" + normalizedName + "' already exists", ex);
            }
            throw ex;
        }

        CommunityMember member = new CommunityMember(
            community.getId(),
            creatorUserId,
            Instant.now()
        );
        communityMemberRepository.save(member);

        return new CommunityResponse(
            community.getId(),
            community.getName(),
            community.getDisplayName(),
            community.getDescription(),
            community.getCreatedBy(),
            1L,
            community.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public CommunityResponse getCommunity(UUID communityId) {
        Community community = communityRepository.findById(communityId)
            .orElseThrow(() -> new CommunityNotFoundException("Community not found with ID: " + communityId));
        long memberCount = communityMemberRepository.countByCommunityId(communityId);
        return new CommunityResponse(
            community.getId(),
            community.getName(),
            community.getDisplayName(),
            community.getDescription(),
            community.getCreatedBy(),
            memberCount,
            community.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommunityResponse> getCommunities(int page, int size) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (page < 0) {
            fieldErrors.put("page", "Page index must not be less than zero");
        }
        if (size < 1) {
            fieldErrors.put("size", "Page size must be at least 1");
        } else if (size > 100) {
            fieldErrors.put("size", "Page size must not exceed 100");
        }
        if (!fieldErrors.isEmpty()) {
            throw new InvalidPaginationException("Invalid pagination parameters", fieldErrors);
        }

        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Community> communityPage = communityRepository.findAll(pageable);

        if (communityPage.isEmpty()) {
            return new PagedResponse<>(
                List.of(),
                communityPage.getNumber(),
                communityPage.getSize(),
                communityPage.getTotalElements(),
                communityPage.getTotalPages(),
                communityPage.isLast()
            );
        }

        List<UUID> communityIds = communityPage.getContent().stream()
            .map(Community::getId)
            .toList();

        List<Object[]> memberCounts = communityMemberRepository.countMembersByCommunityIds(communityIds);
        Map<UUID, Long> countMap = memberCounts.stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]
            ));

        List<CommunityResponse> content = communityPage.getContent().stream()
            .map(c -> new CommunityResponse(
                c.getId(),
                c.getName(),
                c.getDisplayName(),
                c.getDescription(),
                c.getCreatedBy(),
                countMap.getOrDefault(c.getId(), 0L),
                c.getCreatedAt()
            ))
            .toList();

        return new PagedResponse<>(
            content,
            communityPage.getNumber(),
            communityPage.getSize(),
            communityPage.getTotalElements(),
            communityPage.getTotalPages(),
            communityPage.isLast()
        );
    }

    @Transactional
    public void joinCommunity(UUID communityId, UUID userId) {
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityNotFoundException("Community not found with ID: " + communityId);
        }
        communityMemberRepository.insertMemberIfAbsent(communityId, userId, Instant.now());
    }

    @Transactional
    public void leaveCommunity(UUID communityId, UUID userId) {
        if (!communityRepository.existsById(communityId)) {
            throw new CommunityNotFoundException("Community not found with ID: " + communityId);
        }
        communityMemberRepository.deleteByCommunityIdAndUserId(communityId, userId);
    }

    private boolean isUniqueNameViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        Throwable rootCause = ex.getRootCause();
        if (rootCause != null && rootCause.getMessage() != null) {
            msg += " " + rootCause.getMessage().toLowerCase(Locale.ROOT);
        }
        return msg.contains("uk_communities_name");
    }
}
