package com.yasir.miniecommerce.config.security;

import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.UserRepository;
import com.yasir.miniecommerce.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationEntryPoint entryPoint =
            new JwtAuthenticationEntryPoint();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService,
            userRepository,
            entryPoint
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validUserToken_shouldAuthenticateUserWithoutWritingPersonalDataToConsole()
            throws Exception {
        String email = "user@example.test";
        String token = "valid-test-token";
        when(jwtService.extractEmail(token)).thenReturn(email);
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(new User(email, "unused", "USER")));

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        CapturedOutput output = captureOutput(() -> filter.doFilter(
                request,
                response,
                chain
        ));

        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        assertNotNull(authentication);
        assertEquals(email, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_USER")));
        assertEquals(200, response.getStatus());
        assertEquals("", output.stdout());
        assertEquals("", output.stderr());
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidToken_shouldReturnGeneric401WithoutLoggingOrExposingDetails()
            throws Exception {
        String token = "sensitive-test-token";
        String exceptionDetail = "sensitive-parser-detail";
        when(jwtService.extractEmail(token))
                .thenThrow(new IllegalArgumentException(exceptionDetail));

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        CapturedOutput output = captureOutput(() -> filter.doFilter(
                request,
                response,
                chain
        ));

        String responseBody = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(responseBody.contains("Kimlik doğrulama gerekli veya JWT token geçersiz."));
        assertFalse(responseBody.contains(token));
        assertFalse(responseBody.contains(exceptionDetail));
        assertEquals("", output.stdout());
        assertEquals("", output.stderr());
        assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/products");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private CapturedOutput captureOutput(ThrowingRunnable action) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            action.run();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        return new CapturedOutput(
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8)
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private record CapturedOutput(String stdout, String stderr) {
    }
}
