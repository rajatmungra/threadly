package com.threadly.identity.controller;

import com.threadly.identity.config.SecurityConfig;
import com.threadly.identity.dto.response.UserResponse;
import com.threadly.identity.entity.UserRole;
import com.threadly.identity.exception.CustomAuthenticationEntryPoint;
import com.threadly.identity.exception.UserNotFoundException;
import com.threadly.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldReturnCurrentUserWhenAuthenticatedWithValidJwt() throws Exception {
        UUID userId = UUID.randomUUID();
        UserResponse response = new UserResponse(
            userId,
            "john_doe",
            "john@example.com",
            "John Doe",
            "Software engineer",
            UserRole.USER,
            Instant.now()
        );

        when(userService.getUserById(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("john_doe"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.displayName").value("John Doe"))
            .andExpect(jsonPath("$.bio").value("Software engineer"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.tokenHash").doesNotExist());
    }

    @Test
    void shouldReturn401WhenAccessingMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenInvalidJwtProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenJwtSubjectIsNotAValidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .with(jwt().jwt(jwt -> jwt.subject("not-a-valid-uuid"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldReturn404WhenAuthenticatedUserDoesNotExistInDatabase() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId))
            .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/me")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("User not found"))
            .andExpect(jsonPath("$.path").value("/api/v1/users/me"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
