package com.threadly.identity.service;

import com.threadly.identity.dto.request.LoginRequest;
import com.threadly.identity.dto.request.RefreshTokenRequest;
import com.threadly.identity.dto.response.LoginResponse;
import com.threadly.identity.entity.RefreshToken;
import com.threadly.identity.entity.User;
import com.threadly.identity.entity.UserRole;
import com.threadly.identity.exception.InvalidCredentialsException;
import com.threadly.identity.exception.InvalidRefreshTokenException;
import com.threadly.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginSuccessfullyWithUsername() {
        LoginRequest request = new LoginRequest("john_doe", "password123");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setPasswordHash("$2a$10$hashedPassword");
        user.setRole(UserRole.USER);

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(user.getId())).thenReturn("mock.refresh.token");
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("mock.refresh.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.refreshExpiresIn()).isEqualTo(604800L);
    }

    @Test
    void shouldLoginSuccessfullyWithEmail() {
        LoginRequest request = new LoginRequest("  JOHN@EXAMPLE.COM  ", "password123");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setPasswordHash("$2a$10$hashedPassword");
        user.setRole(UserRole.USER);

        when(userRepository.findByUsername("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$10$hashedPassword")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("mock.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(user.getId())).thenReturn("mock.refresh.token");
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        LoginResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("mock.refresh.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.refreshExpiresIn()).isEqualTo(604800L);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenPasswordIsWrong() {
        LoginRequest request = new LoginRequest("john_doe", "wrongPassword");
        User user = new User();
        user.setUsername("john_doe");
        user.setPasswordHash("$2a$10$hashedPassword");

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid credentials");

        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void shouldThrowSameGenericInvalidCredentialsWhenUserDoesNotExist() {
        LoginRequest request = new LoginRequest("unknown_user", "password123");

        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown_user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid credentials");

        verify(jwtService, never()).generateAccessToken(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void shouldRefreshSuccessfullyWithValidToken() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("john_doe");
        user.setRole(UserRole.USER);

        RefreshToken oldToken = new RefreshToken(userId, "old_hash", Instant.now().plusSeconds(3600));

        when(refreshTokenService.getActiveTokenForUpdate("raw_old_token")).thenReturn(oldToken);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(refreshTokenService.createRefreshToken(userId)).thenReturn("raw_new_token");
        when(jwtService.generateAccessToken(user)).thenReturn("new.access.token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.getRefreshExpirationSeconds()).thenReturn(604800L);

        LoginResponse response = authService.refresh(new RefreshTokenRequest("raw_old_token"));

        assertThat(response.accessToken()).isEqualTo("new.access.token");
        assertThat(response.refreshToken()).isEqualTo("raw_new_token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.refreshExpiresIn()).isEqualTo(604800L);

        verify(refreshTokenService).revokeToken(oldToken);
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenUserNotFoundDuringRefresh() {
        UUID userId = UUID.randomUUID();
        RefreshToken oldToken = new RefreshToken(userId, "old_hash", Instant.now().plusSeconds(3600));

        when(refreshTokenService.getActiveTokenForUpdate("raw_old_token")).thenReturn(oldToken);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("raw_old_token")))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token");

        verify(refreshTokenService, never()).revokeToken(any());
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void shouldLogoutSuccessfully() {
        authService.logout(new RefreshTokenRequest("raw_token_to_logout"));
        verify(refreshTokenService).revokeByRawToken("raw_token_to_logout");
    }
}
