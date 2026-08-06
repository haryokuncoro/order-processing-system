package com.haryokuncoro.ops.service;

import com.haryokuncoro.ops.dto.RegisterRequest;
import com.haryokuncoro.ops.entity.User;
import com.haryokuncoro.ops.exception.BadRequestException;
import com.haryokuncoro.ops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
        import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void register_ShouldRegisterUserSuccessfully() {

        String username = "test";
        String email = "test@example.com";
        String password = "Password1";
        String encodedPassword = "encoded-password";
        String token = "jwt-token";

        when(userRepo.findByEmail(email)).thenReturn(Optional.empty());
        when(encoder.encode(password)).thenReturn(encodedPassword);

        when(userRepo.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        when(jwtService.generate(userId)).thenReturn(token);

        String result = authService.register(
                RegisterRequest.builder()
                        .username(username)
                        .email(email)
                        .password(password)
                        .build()
        );

        assertEquals(token, result);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());

        User savedUser = captor.getValue();

        assertEquals(email, savedUser.getEmail());
        assertEquals(encodedPassword, savedUser.getPassword());

        verify(jwtService).generate(userId);
    }

    @Test
    void register_ShouldThrow_WhenEmailAlreadyExists() {

        String email = "test@example.com";
        String username = "test";

        User existing = new User();
        existing.setEmail(email);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(existing));
        RegisterRequest request = RegisterRequest.builder()
                .username(username)
                .email(email)
                .password("Password1")
                .build();
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.register(request)
        );

        assertEquals("User already registered. Please Login", ex.getMessage());

        verify(userRepo, never()).save(any());
        verify(jwtService, never()).generate(any());
    }

    @Test
    void register_ShouldThrow_WhenPasswordIsWeak() {

        String email = "test@example.com";

        when(userRepo.findByEmail(email)).thenReturn(Optional.empty());
        RegisterRequest request = RegisterRequest.builder()
                .username("username")
                .email(email)
                .password("weak")
                .build();
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> authService.register(request)
        );

        assertEquals("Weak password", ex.getMessage());

        verify(userRepo, never()).save(any());
        verify(jwtService, never()).generate(any());
    }

    @Test
    void login_ShouldReturnToken() {

        String email = "test@example.com";
        String rawPassword = "Password1";
        String encodedPassword = "encoded";
        String token = "jwt-token";

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPassword(encodedPassword);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(encoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(jwtService.generate(userId)).thenReturn(token);

        String result = authService.login(email, rawPassword);

        assertEquals(token, result);

        verify(jwtService).generate(userId);
    }

    @Test
    void login_ShouldThrow_WhenUserNotFound() {

        when(userRepo.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.login("test@example.com", "Password1")
        );

        assertEquals("User not found", ex.getMessage());

        verify(jwtService, never()).generate(any());
    }

    @Test
    void login_ShouldThrow_WhenPasswordIsInvalid() {

        String email = "test@example.com";

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setPassword("encoded");

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(encoder.matches("Password1", "encoded")).thenReturn(false);

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.login(email, "Password1")
        );

        assertEquals("Wrong Username or password", ex.getMessage());

        verify(jwtService, never()).generate(any());
    }
}