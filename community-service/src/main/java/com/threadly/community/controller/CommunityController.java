package com.threadly.community.controller;

import com.threadly.community.dto.request.CreateCommunityRequest;
import com.threadly.community.dto.response.CommunityResponse;
import com.threadly.community.dto.response.PagedResponse;
import com.threadly.community.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityResponse createCommunity(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateCommunityRequest request
    ) {
        UUID userId = parseUserId(jwt);
        return communityService.createCommunity(request, userId);
    }

    @GetMapping("/{communityId}")
    public CommunityResponse getCommunity(@PathVariable UUID communityId) {
        return communityService.getCommunity(communityId);
    }

    @GetMapping
    public PagedResponse<CommunityResponse> getCommunities(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return communityService.getCommunities(page, size);
    }

    @PostMapping("/{communityId}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void joinCommunity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID communityId
    ) {
        UUID userId = parseUserId(jwt);
        communityService.joinCommunity(communityId, userId);
    }

    @DeleteMapping("/{communityId}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveCommunity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID communityId
    ) {
        UUID userId = parseUserId(jwt);
        communityService.leaveCommunity(communityId, userId);
    }

    private UUID parseUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidBearerTokenException("Missing token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Invalid token subject format");
        }
    }
}
