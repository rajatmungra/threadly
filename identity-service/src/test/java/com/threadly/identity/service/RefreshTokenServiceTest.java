package com.threadly.identity.service;

import com.threadly.identity.entity.RefreshToken;
import com.threadly.identity.exception.InvalidRefreshTokenException;
import com.threadly.identity.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private static final long REFRESH_EXPIRATION_SECONDS = 604800L;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, REFRESH_EXPIRATION_SECONDS);
    }

    @Test
    void shouldCreateRefreshTokenWithSha256HashAndNeverStoreRawToken() {
        UUID userId = UUID.randomUUID();

        String rawToken = refreshTokenService.createRefreshToken(userId);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedToken = captor.getValue();
        assertThat(savedToken.getUserId()).isEqualTo(userId);
        assertThat(savedToken.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedToken.getTokenHash()).hasSize(64);
        assertThat(savedToken.getTokenHash()).isEqualTo(refreshTokenService.hashToken(rawToken));
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now().plusSeconds(REFRESH_EXPIRATION_SECONDS - 5));
    }

    @Test
    void shouldGetActiveTokenForUpdateWhenValid() {
        String rawToken = "raw_test_token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken token = new RefreshToken(UUID.randomUUID(), tokenHash, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.getActiveTokenForUpdate(rawToken);

        assertThat(result).isSameAs(token);
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenHashNotFound() {
        String rawToken = "non_existent_token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.getActiveTokenForUpdate(rawToken))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenIsExpired() {
        String rawToken = "expired_token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = new RefreshToken(UUID.randomUUID(), tokenHash, Instant.now().minusSeconds(10));

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.getActiveTokenForUpdate(rawToken))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenTokenIsRevoked() {
        String rawToken = "revoked_token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken revokedToken = new RefreshToken(UUID.randomUUID(), tokenHash, Instant.now().plusSeconds(3600));
        revokedToken.revoke();

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.getActiveTokenForUpdate(rawToken))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void shouldThrowInvalidRefreshTokenWhenRawTokenIsBlank() {
        assertThatThrownBy(() -> refreshTokenService.getActiveTokenForUpdate("   "))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository, never()).findByTokenHashForUpdate(any());
    }

    @Test
    void shouldRevokeTokenAndSave() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), "hash", Instant.now().plusSeconds(3600));

        refreshTokenService.revokeToken(token);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldRevokeByRawTokenWhenActive() {
        String rawToken = "active_token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken token = new RefreshToken(UUID.randomUUID(), tokenHash, Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeByRawToken(rawToken);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldDoNothingWhenRevokingNonExistentToken() {
        String rawToken = "non_existent";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        refreshTokenService.revokeByRawToken(rawToken);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldDoNothingWhenRevokingAlreadyRevokedToken() {
        String rawToken = "already_revoked";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken token = new RefreshToken(UUID.randomUUID(), tokenHash, Instant.now().plusSeconds(3600));
        token.revoke();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeByRawToken(rawToken);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldReturnConfiguredRefreshExpirationSeconds() {
        assertThat(refreshTokenService.getRefreshExpirationSeconds()).isEqualTo(REFRESH_EXPIRATION_SECONDS);
    }
}
