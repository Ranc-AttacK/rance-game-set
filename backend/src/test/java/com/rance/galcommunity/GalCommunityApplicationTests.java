package com.rance.galcommunity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class GalCommunityApplicationTests extends IntegrationTestBase {

    @Test
    void contextLoadsAndHealthEndpointWorks() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
