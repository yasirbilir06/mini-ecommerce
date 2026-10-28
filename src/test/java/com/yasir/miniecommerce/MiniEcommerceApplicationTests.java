package com.yasir.miniecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState,db",
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.show-components=never"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MiniEcommerceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void readinessHealth_shouldBePublicAndHideComponentDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void openApiDocumentation_shouldMatchEndpointSecurityRules() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/orders'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/orders'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/orders/{id}'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/orders/{id}/cancel'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/orders/{id}/status'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

}
