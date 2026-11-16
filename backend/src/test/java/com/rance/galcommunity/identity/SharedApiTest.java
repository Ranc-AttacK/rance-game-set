package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.rance.galcommunity.IntegrationTestBase;
import com.rance.galcommunity.support.ProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

@Import(ProbeController.class)
@WithMockUser
class SharedApiTest extends IntegrationTestBase {

    @Test
    void validationErrorUsesEnvelopeAndTraceId() throws Exception {
        var result = mockMvc.perform(post("/test/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn();

        String bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo(bodyTraceId);
    }

    @Test
    void businessErrorUses422() throws Exception {
        mockMvc.perform(get("/test/probe/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void unexpectedErrorIsMasked() throws Exception {
        mockMvc.perform(get("/test/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50001))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }
}
