package com.threadly.identity.service;

import com.threadly.identity.dto.request.LoginRequest;
import com.threadly.identity.dto.request.RefreshTokenRequest;
import com.threadly.identity.dto.response.LoginResponse;
import com.threadly.identity.entity.RefreshToken;
import com.threadly.identity.entity.User;
import com.threadly.identity.exception.InvalidCredentialsException;
import com.threadly.identity.exception.InvalidRefreshTokenException;
import com.threadly.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String normalizedIdentifier = request.identifier().trim().toLowerCase(Locale.ROOT);

        Optional<User> userOpt = userRepository.findByUsername(normalizedIdentifier)
            .or(() -> userRepository.findByEmail(normalizedIdentifier));

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        User user = userOpt.get();
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new LoginResponse(
            accessToken,
            refreshToken,
            "Bearer",
            jwtService.getExpirationSeconds(),
            refreshTokenService.getRefreshExpirationSeconds()
        );
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshToken oldToken = refreshTokenService.getActiveTokenForUpdate(request.refreshToken());

        User user = userRepository.findById(oldToken.getUserId())
            .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        refreshTokenService.revokeToken(oldToken);

        String newRefreshToken = refreshTokenService.createRefreshToken(user.getId());
        String newAccessToken = jwtService.generateAccessToken(user);

        return new LoginResponse(
            newAccessToken,
            newRefreshToken,
            "Bearer",
            jwtService.getExpirationSeconds(),
            refreshTokenService.getRefreshExpirationSeconds()
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeByRawToken(request.refreshToken());
    }
}
