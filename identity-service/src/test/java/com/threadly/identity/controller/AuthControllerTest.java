package com.threadly.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threadly.identity.config.SecurityConfig;
import com.threadly.identity.dto.request.LoginRequest;
import com.threadly.identity.dto.request.RefreshTokenRequest;
import com.threadly.identity.dto.request.RegisterRequest;
import com.threadly.identity.dto.response.LoginResponse;
import com.threadly.identity.dto.response.UserResponse;
import com.threadly.identity.entity.UserRole;
import com.threadly.identity.exception.CustomAuthenticationEntryPoint;
import com.threadly.identity.exception.DuplicateEmailException;
import com.threadly.identity.exception.DuplicateUsernameException;
import com.threadly.identity.exception.InvalidCredentialsException;
import com.threadly.identity.exception.InvalidRefreshTokenException;
import com.threadly.identity.service.AuthService;
import com.threadly.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldRegisterPubliclyWithoutAuthentication() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        UUID userId = UUID.randomUUID();
        UserResponse response = new UserResponse(
            userId,
            "john_doe",
            "john@example.com",
            "John",
            null,
            UserRole.USER,
            Instant.now()
        );

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.username").value("john_doe"))
            .andExpect(jsonPath("$.email").value("john@example.com"))
            .andExpect(jsonPath("$.displayName").value("John"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldLoginPubliclyWithUsername() throws Exception {
        LoginRequest request = new LoginRequest("john_doe", "strongPassword123");
        LoginResponse response = new LoginResponse("mock.access.token", "mock.refresh.token", "Bearer", 900L, 604800L);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("mock.access.token"))
            .andExpect(jsonPath("$.refreshToken").value("mock.refresh.token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.refreshExpiresIn").value(604800));
    }

    @Test
    void shouldLoginPubliclyWithEmail() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "strongPassword123");
        LoginResponse response = new LoginResponse("mock.access.token", "mock.refresh.token", "Bearer", 900L, 604800L);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("mock.access.token"))
            .andExpect(jsonPath("$.refreshToken").value("mock.refresh.token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.refreshExpiresIn").value(604800));
    }

    @Test
    void shouldReturn401WhenLoginHasWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest("john_doe", "wrongPassword");

        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnSame401WhenLoginHasUnknownIdentifier() throws Exception {
        LoginRequest request = new LoginRequest("unknown_user", "password123");

        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldRefreshPubliclyWithValidToken() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid_refresh_token");
        LoginResponse response = new LoginResponse("new.access.token", "new.refresh.token", "Bearer", 900L, 604800L);

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new.access.token"))
            .andExpect(jsonPath("$.refreshToken").value("new.refresh.token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.refreshExpiresIn").value(604800));
    }

    @Test
    void shouldReturn401WhenRefreshTokenIsInvalid() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid_or_expired_token");

        when(authService.refresh(any(RefreshTokenRequest.class)))
            .thenThrow(new InvalidRefreshTokenException("Invalid refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
            .andExpect(jsonPath("$.message").value("Invalid refresh token"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/refresh"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenRefreshTokenIsBlank() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.refreshToken").exists());
    }

    @Test
    void shouldLogoutPubliclyAndReturn204() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("token_to_logout");
        doNothing().when(authService).logout(any(RefreshTokenRequest.class));

        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn400WhenLogoutRefreshTokenIsBlank() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("");

        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.refreshToken").exists());
    }

    @Test
    void shouldReturn401WhenProtectedEndpointAccessedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn400WhenLoginIdentifierIsBlank() throws Exception {
        LoginRequest request = new LoginRequest("", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.identifier").exists());
    }

    @Test
    void shouldReturn400WhenLoginPasswordIsBlank() throws Exception {
        LoginRequest request = new LoginRequest("john_doe", "");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void shouldReturn400WhenUsernameIsBlank() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "not-an-email",
            "strongPassword123",
            "John"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void shouldReturn400WhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "short",
            "John"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void shouldReturn409WhenUsernameAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userService.register(any(RegisterRequest.class)))
            .thenThrow(new DuplicateUsernameException("Username already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Username already exists"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userService.register(any(RegisterRequest.class)))
            .thenThrow(new DuplicateEmailException("Email already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Email already exists"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400WhenRegisterPayloadIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{malformed_json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Malformed request payload"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn500WhenUnrelatedDataIntegrityViolationOccurs() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userService.register(any(RegisterRequest.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("check constraint violation on users_check"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn409WhenDataIntegrityViolationWithUsernameConstraintOccurs() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userService.register(any(RegisterRequest.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("violates unique constraint uk_users_username"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Username already exists"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn409WhenDataIntegrityViolationWithEmailConstraintOccurs() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userService.register(any(RegisterRequest.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("violates unique constraint uk_users_email"));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("Email already exists"))
            .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
