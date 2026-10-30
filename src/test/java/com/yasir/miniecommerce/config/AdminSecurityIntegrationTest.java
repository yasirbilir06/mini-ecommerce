
package com.yasir.miniecommerce.config;

import com.yasir.miniecommerce.model.User;
import com.yasir.miniecommerce.repository.UserRepository;
import com.yasir.miniecommerce.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void requestWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userToken_shouldReturn403() throws Exception {
        String email = "user@example.test";
        String token = jwtService.generateToken(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(
                        new User(email, "unused", "USER")
                ));

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminToken_shouldReturn200() throws Exception {
        String email = "admin@example.test";
        String token = jwtService.generateToken(email);

        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(
                        new User(email, "unused", "ADMIN")
                ));

        mockMvc.perform(get("/api/admin/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Admin erişimi başarılı!"));
    }
}
