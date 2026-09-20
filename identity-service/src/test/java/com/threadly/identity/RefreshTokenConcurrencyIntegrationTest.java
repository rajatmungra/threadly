package com.threadly.identity;

import com.threadly.identity.dto.request.RefreshTokenRequest;
import com.threadly.identity.entity.RefreshToken;
import com.threadly.identity.entity.User;
import com.threadly.identity.entity.UserRole;
import com.threadly.identity.exception.InvalidRefreshTokenException;
import com.threadly.identity.repository.RefreshTokenRepository;
import com.threadly.identity.repository.UserRepository;
import com.threadly.identity.service.AuthService;
import com.threadly.identity.service.RefreshTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenConcurrencyIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User(
            "concurrent_user",
            "concurrent@example.com",
            passwordEncoder.encode("Password123"),
            UserRole.USER
        );
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldAllowExactlyOneSuccessfulRefreshUnderConcurrentAttempts() throws Exception {
        String rawToken = refreshTokenService.createRefreshToken(testUser.getId());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<Throwable> failureReason = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    authService.refresh(new RefreshTokenRequest(rawToken));
                    successCount.incrementAndGet();
                } catch (InvalidRefreshTokenException e) {
                    failureCount.incrementAndGet();
                    failureReason.set(e);
                } catch (Throwable t) {
                    failureReason.set(t);
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
        assertThat(failureReason.get()).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(failureReason.get().getMessage()).isEqualTo("Invalid refresh token");

        String tokenHash = refreshTokenService.hashToken(rawToken);
        RefreshToken storedOriginalToken = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(storedOriginalToken.isRevoked()).isTrue();
        assertThat(storedOriginalToken.getRevokedAt()).isNotNull();
    }
}
