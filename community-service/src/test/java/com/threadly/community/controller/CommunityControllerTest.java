package com.threadly.community.controller;

import com.threadly.community.config.SecurityConfig;
import com.threadly.community.dto.request.CreateCommunityRequest;
import com.threadly.community.dto.response.CommunityResponse;
import com.threadly.community.exception.CustomAuthenticationEntryPoint;
import com.threadly.community.exception.DuplicateCommunityNameException;
import com.threadly.community.exception.GlobalExceptionHandler;
import com.threadly.community.service.CommunityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import com.threadly.community.dto.response.PagedResponse;
import com.threadly.community.exception.CommunityNotFoundException;
import com.threadly.community.exception.InvalidPaginationException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommunityController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class CommunityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityService communityService;

    @Test
    void shouldCreateCommunityWhenAuthenticatedWithValidJwt() throws Exception {
        UUID creatorId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        CommunityResponse response = new CommunityResponse(
            communityId,
            "springboot",
            "Spring Boot",
            "Discussion about Spring Boot",
            creatorId,
            1L,
            createdAt
        );

        when(communityService.createCommunity(any(CreateCommunityRequest.class), eq(creatorId)))
            .thenReturn(response);

        String requestBody = """
            {
                "name": "springboot",
                "displayName": "Spring Boot",
                "description": "Discussion about Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(communityId.toString()))
            .andExpect(jsonPath("$.name").value("springboot"))
            .andExpect(jsonPath("$.displayName").value("Spring Boot"))
            .andExpect(jsonPath("$.description").value("Discussion about Spring Boot"))
            .andExpect(jsonPath("$.createdBy").value(creatorId.toString()))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.createdAt").exists());

        verify(communityService).createCommunity(any(CreateCommunityRequest.class), eq(creatorId));
    }

    @Test
    void shouldReturn401WhenRequestingWithoutToken() throws Exception {
        String requestBody = """
            {
                "name": "springboot",
                "displayName": "Spring Boot",
                "description": "Discussion about Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenInvalidJwtProvided() throws Exception {
        String requestBody = """
            {
                "name": "springboot",
                "displayName": "Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .header("Authorization", "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenJwtSubjectIsNotAValidUuid() throws Exception {
        String requestBody = """
            {
                "name": "springboot",
                "displayName": "Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid-format")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn409WhenCommunityNameAlreadyExists() throws Exception {
        UUID creatorId = UUID.randomUUID();
        when(communityService.createCommunity(any(CreateCommunityRequest.class), eq(creatorId)))
            .thenThrow(new DuplicateCommunityNameException("Community with name 'springboot' already exists"));

        String requestBody = """
            {
                "name": "springboot",
                "displayName": "Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("COMMUNITY_NAME_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Community with name 'springboot' already exists"));
    }

    @Test
    void shouldReturn400WhenNameIsBlank() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String requestBody = """
            {
                "name": "   ",
                "displayName": "Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void shouldReturn400WhenNameContainsInvalidCharacters() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String requestBody = """
            {
                "name": "spring-boot!",
                "displayName": "Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void shouldReturn400WhenNameIsTooShort() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String requestBody = """
            {
                "name": "sp",
                "displayName": "Spring Boot"
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void shouldReturn400WhenDisplayNameIsBlank() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String requestBody = """
            {
                "name": "springboot",
                "displayName": "  "
            }
            """;

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    void shouldReturn400WhenDescriptionExceedsMaxLength() throws Exception {
        UUID creatorId = UUID.randomUUID();
        String longDescription = "a".repeat(501);
        String requestBody = """
            {
                "name": "springboot",
                "displayName": "Spring Boot",
                "description": "%s"
            }
            """.formatted(longDescription);

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.description").exists());
    }

    @Test
    void shouldReturnCommunityWhenExistsAndAuthenticated() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        CommunityResponse response = new CommunityResponse(
            communityId,
            "springboot",
            "Spring Boot",
            "Discussion about Spring Boot",
            creatorId,
            5L,
            Instant.now()
        );

        when(communityService.getCommunity(communityId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/communities/{communityId}", communityId)
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(communityId.toString()))
            .andExpect(jsonPath("$.name").value("springboot"))
            .andExpect(jsonPath("$.displayName").value("Spring Boot"))
            .andExpect(jsonPath("$.description").value("Discussion about Spring Boot"))
            .andExpect(jsonPath("$.createdBy").value(creatorId.toString()))
            .andExpect(jsonPath("$.memberCount").value(5))
            .andExpect(jsonPath("$.createdAt").exists());

        verify(communityService).getCommunity(communityId);
    }

    @Test
    void shouldReturn404WhenCommunityDoesNotExist() throws Exception {
        UUID communityId = UUID.randomUUID();
        when(communityService.getCommunity(communityId))
            .thenThrow(new CommunityNotFoundException("Community not found with ID: " + communityId));

        mockMvc.perform(get("/api/v1/communities/{communityId}", communityId)
                .with(jwt()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Community not found with ID: " + communityId));
    }

    @Test
    void shouldReturn401WhenGetCommunityWithoutToken() throws Exception {
        UUID communityId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/communities/{communityId}", communityId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenCommunityIdIsMalformed() throws Exception {
        mockMvc.perform(get("/api/v1/communities/{communityId}", "not-a-valid-uuid")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.communityId").exists());
    }

    @Test
    void shouldReturnPagedCommunitiesWithDefaults() throws Exception {
        CommunityResponse item = new CommunityResponse(
            UUID.randomUUID(),
            "springboot",
            "Spring Boot",
            "Description",
            UUID.randomUUID(),
            3L,
            Instant.now()
        );
        PagedResponse<CommunityResponse> pagedResponse = new PagedResponse<>(
            List.of(item),
            0,
            20,
            1L,
            1,
            true
        );

        when(communityService.getCommunities(0, 20)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/communities")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("springboot"))
            .andExpect(jsonPath("$.content[0].memberCount").value(3))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.last").value(true));

        verify(communityService).getCommunities(0, 20);
    }

    @Test
    void shouldReturnPagedCommunitiesWithCustomPageAndSize() throws Exception {
        PagedResponse<CommunityResponse> pagedResponse = new PagedResponse<>(
            List.of(),
            2,
            10,
            25L,
            3,
            false
        );

        when(communityService.getCommunities(2, 10)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/communities")
                .param("page", "2")
                .param("size", "10")
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(25))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.last").value(false));

        verify(communityService).getCommunities(2, 10);
    }

    @Test
    void shouldReturn401WhenGetCommunitiesWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/communities"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenPaginationParametersAreInvalid() throws Exception {
        when(communityService.getCommunities(-1, 20))
            .thenThrow(new InvalidPaginationException(
                "Invalid pagination parameters",
                Map.of("page", "Page index must not be less than zero")
            ));

        mockMvc.perform(get("/api/v1/communities")
                .param("page", "-1")
                .with(jwt()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.page").value("Page index must not be less than zero"));
    }

    @Test
    void shouldJoinCommunityWhenAuthenticatedWithValidJwt() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/communities/{communityId}/join", communityId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(communityService).joinCommunity(communityId, userId);
    }

    @Test
    void shouldReturn404WhenJoiningUnknownCommunity() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new CommunityNotFoundException("Community not found with ID: " + communityId))
            .when(communityService).joinCommunity(communityId, userId);

        mockMvc.perform(post("/api/v1/communities/{communityId}/join", communityId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void shouldReturn401WhenJoiningWithoutToken() throws Exception {
        UUID communityId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/communities/{communityId}/join", communityId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenJoiningWithMalformedJwtSubject() throws Exception {
        UUID communityId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/communities/{communityId}/join", communityId)
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldLeaveCommunityWhenAuthenticatedWithValidJwt() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/communities/{communityId}/join", communityId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(communityService).leaveCommunity(communityId, userId);
    }

    @Test
    void shouldReturn404WhenLeavingUnknownCommunity() throws Exception {
        UUID communityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        org.mockito.Mockito.doThrow(new CommunityNotFoundException("Community not found with ID: " + communityId))
            .when(communityService).leaveCommunity(communityId, userId);

        mockMvc.perform(delete("/api/v1/communities/{communityId}/join", communityId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void shouldReturn401WhenLeavingWithoutToken() throws Exception {
        UUID communityId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/communities/{communityId}/join", communityId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn401WhenLeavingWithMalformedJwtSubject() throws Exception {
        UUID communityId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/communities/{communityId}/join", communityId)
                .with(jwt().jwt(jwt -> jwt.subject("not-a-valid-uuid"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn400WhenCreateCommunityPayloadIsMalformedJson() throws Exception {
        UUID creatorId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/communities")
                .with(jwt().jwt(jwt -> jwt.subject(creatorId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{malformed_json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Malformed request payload"))
            .andExpect(jsonPath("$.path").value("/api/v1/communities"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
