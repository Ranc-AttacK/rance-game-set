package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rance.galcommunity.IntegrationTestBase;
import org.junit.jupiter.api.Test;

class ApiDocsIT extends IntegrationTestBase {

    @Test
    void openApiDocumentIsAvailable() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Gal Community API");
        assertThat(body).contains("bearerAuth");
    }

    @Test
    void healthIsPublicAndActuatorMetricsRequireAdmin() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }
}
