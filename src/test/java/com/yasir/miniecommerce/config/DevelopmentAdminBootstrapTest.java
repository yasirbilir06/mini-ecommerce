package com.yasir.miniecommerce.config;

import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevelopmentAdminBootstrapTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "development-password";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void run_shouldCreateAdminWithEncodedPassword_whenNoUserExists() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD).run(new DefaultApplicationArguments());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedAdmin = userCaptor.getValue();
        assertEquals(ADMIN_EMAIL, savedAdmin.getEmail());
        assertEquals("ADMIN", savedAdmin.getRole());
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, savedAdmin.getPassword()));
    }

    @Test
    void run_shouldNotCreateAnotherAdmin_whenAdminAlreadyExists() {
        User existingAdmin = new User();
        existingAdmin.setRole("ADMIN");
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(existingAdmin));

        bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD).run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_shouldFailWithoutChangingRole_whenEmailBelongsToNonAdmin() {
        User existingUser = new User();
        existingUser.setRole("USER");
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(existingUser));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD)
                        .run(new DefaultApplicationArguments())
        );

        assertTrue(exception.getMessage().contains("rolü otomatik olarak değiştirilemez"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_shouldFailWhenEmailIsMissing() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrap(" ", ADMIN_PASSWORD)
                        .run(new DefaultApplicationArguments())
        );

        assertTrue(exception.getMessage().contains("BOOTSTRAP_ADMIN_EMAIL"));
        verify(userRepository, never()).findByEmail(ADMIN_EMAIL);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_shouldFailWhenPasswordIsTooShort() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrap(ADMIN_EMAIL, "short")
                        .run(new DefaultApplicationArguments())
        );

        assertTrue(exception.getMessage().contains("en az 12 karakter"));
        verify(userRepository, never()).findByEmail(ADMIN_EMAIL);
        verify(userRepository, never()).save(any(User.class));
    }

    private DevelopmentAdminBootstrap bootstrap(String email, String password) {
        return new DevelopmentAdminBootstrap(
                userRepository,
                passwordEncoder,
                email,
                password
        );
    }
}
