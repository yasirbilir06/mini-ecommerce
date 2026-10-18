package com.yasir.miniecommerce.service;

import com.yasir.miniecommerce.dto.RegisterRequest;
import com.yasir.miniecommerce.dto.RegisterResponse;
import com.yasir.miniecommerce.exception.DuplicateEmailException;
import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_shouldThrowDuplicateEmailException_whenEmailAlreadyExists() {
        String email = "existing@example.com";
        RegisterRequest request = registerRequest(email, "password123");

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(new User()));

        DuplicateEmailException exception = assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(request)
        );

        assertEquals("Bu e-posta adresi zaten kullanılıyor", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldSaveEncodedPasswordAndUserRole_whenEmailIsNew() {
        String email = "new@example.com";
        String rawPassword = "password123";
        RegisterRequest request = registerRequest(email, rawPassword);

        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(email, savedUser.getEmail());
        assertNotEquals(rawPassword, savedUser.getPassword());
        assertTrue(passwordEncoder.matches(rawPassword, savedUser.getPassword()));
        assertEquals("USER", savedUser.getRole());
        assertEquals(email, response.getEmail());
        assertEquals("USER", response.getRole());
    }

    private RegisterRequest registerRequest(String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
