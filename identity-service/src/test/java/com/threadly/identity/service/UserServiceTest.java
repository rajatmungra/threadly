package com.threadly.identity.service;

import com.threadly.identity.dto.request.RegisterRequest;
import com.threadly.identity.dto.response.UserResponse;
import com.threadly.identity.entity.User;
import com.threadly.identity.entity.UserRole;
import com.threadly.identity.exception.DuplicateEmailException;
import com.threadly.identity.exception.DuplicateUsernameException;
import com.threadly.identity.exception.UserNotFoundException;
import com.threadly.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterUserSuccessfullyWithDefaultRoleAndHashedPassword() {
        RegisterRequest request = new RegisterRequest(
            "  John_Doe  ",
            "  John@Example.COM  ",
            "strongPassword123",
            "  John  "
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("strongPassword123")).thenReturn("$2a$10$hashedPasswordSample");

        UUID generatedId = UUID.randomUUID();
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(generatedId);
            return user;
        });

        UserResponse response = userService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User savedUser = captor.getValue();

        assertThat(savedUser.getUsername()).isEqualTo("john_doe");
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(savedUser.getDisplayName()).isEqualTo("John");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$hashedPasswordSample");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("strongPassword123");

        assertThat(response.id()).isEqualTo(generatedId);
        assertThat(response.username()).isEqualTo("john_doe");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.displayName()).isEqualTo("John");
        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void shouldRejectRegistrationWhenUsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(DuplicateUsernameException.class)
            .hasMessage("Username already exists");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectRegistrationWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("Email already exists");

        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void shouldMapConcurrentDatabaseUsernameDuplicateTo409Exception() {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uk_users_username"));

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(DuplicateUsernameException.class)
            .hasMessage("Username already exists");
    }

    @Test
    void shouldMapConcurrentDatabaseEmailDuplicateTo409Exception() {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint uk_users_email"));

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(DuplicateEmailException.class)
            .hasMessage("Email already exists");
    }

    @Test
    void shouldNotTranslateUnrelatedIntegrityViolationToDuplicateException() {
        RegisterRequest request = new RegisterRequest(
            "john_doe",
            "john@example.com",
            "strongPassword123",
            "John"
        );

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
            .thenThrow(new DataIntegrityViolationException("check constraint violation on users_check"));

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("check constraint violation");
    }

    @Test
    void shouldReturnUserResponseWhenUserExists() {
        UUID userId = UUID.randomUUID();
        User user = new User("john_doe", "john@example.com", "hashed", UserRole.USER);
        user.setId(userId);
        user.setDisplayName("John");
        user.setBio("Hello world");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("john_doe");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.displayName()).isEqualTo("John");
        assertThat(response.bio()).isEqualTo("Hello world");
        assertThat(response.role()).isEqualTo(UserRole.USER);
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("User not found");
    }
}
