package com.threadly.identity.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.threadly.identity.entity.User;
import com.threadly.identity.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "test_secret_key_at_least_32_bytes_long_123456";
    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        jwtService = new JwtService(encoder, 900L);
    }

    @Test
    void shouldGenerateJwtWithExpectedClaims() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("john_doe");
        user.setEmail("john@example.com");
        user.setDisplayName("John");
        user.setPasswordHash("$2a$10$hashedPassword");
        user.setRole(UserRole.USER);

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();

        Jwt decodedJwt = jwtDecoder.decode(token);
        assertThat(decodedJwt.getSubject()).isEqualTo(userId.toString());
        assertThat(decodedJwt.getClaimAsString("username")).isEqualTo("john_doe");
        assertThat(decodedJwt.getClaimAsStringList("roles")).containsExactly("USER");

        Instant issuedAt = decodedJwt.getIssuedAt();
        Instant expiresAt = decodedJwt.getExpiresAt();
        assertThat(issuedAt).isNotNull();
        assertThat(expiresAt).isNotNull();
        assertThat(ChronoUnit.SECONDS.between(issuedAt, expiresAt)).isEqualTo(900L);

        assertThat(decodedJwt.getClaims()).doesNotContainKeys("email", "displayName", "password", "passwordHash");
    }

    @Test
    void shouldConfigureExpirationTimeCorrectly() {
        SecretKey secretKey = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        JwtService customJwtService = new JwtService(encoder, 300L);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setRole(UserRole.ADMIN);

        String token = customJwtService.generateAccessToken(user);
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(ChronoUnit.SECONDS.between(decoded.getIssuedAt(), decoded.getExpiresAt())).isEqualTo(300L);
        assertThat(customJwtService.getExpirationSeconds()).isEqualTo(300L);
    }
}
