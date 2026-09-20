package com.threadly.community.controller;

import com.threadly.community.dto.request.CreateCommunityRequest;
import com.threadly.community.dto.response.CommunityResponse;
import com.threadly.community.dto.response.PagedResponse;
import com.threadly.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Communities", description = "Community management and memberships")
@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Operation(summary = "Create a new community")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Community created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Community name already exists")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityResponse createCommunity(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateCommunityRequest request
    ) {
        UUID userId = parseUserId(jwt);
        return communityService.createCommunity(request, userId);
    }

    @Operation(summary = "Get community by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Community retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Community not found")
    })
    @GetMapping("/{communityId}")
    public CommunityResponse getCommunity(@PathVariable UUID communityId) {
        return communityService.getCommunity(communityId);
    }

    @Operation(summary = "List communities with pagination")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Communities retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    public PagedResponse<CommunityResponse> getCommunities(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return communityService.getCommunities(page, size);
    }

    @Operation(summary = "Join a community")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Joined community successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Community not found"),
        @ApiResponse(responseCode = "409", description = "Already a member of the community")
    })
    @PostMapping("/{communityId}/join")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void joinCommunity(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID communityId
    ) {
        UUID userId = parseUserId(jwt);
        communityService.joinCommunity(communityId, userId);
    }

    @Operation(summary = "Leave a community")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Left community successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Community not found or not a member")
    })
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
