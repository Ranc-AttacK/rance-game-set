package com.rance.galcommunity.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Test
    void typeMismatchIs400Envelope() throws Exception {
        mockMvc.perform(get("/test/probe/typed/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void missingParamIs400Envelope() throws Exception {
        mockMvc.perform(get("/test/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void methodNotAllowedIs405Envelope() throws Exception {
        mockMvc.perform(post("/test/probe/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40501))
                .andExpect(header().exists("Allow"));
    }

    @Test
    void unsupportedMediaIs415Envelope() throws Exception {
        mockMvc.perform(post("/test/probe/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41501));
    }

    @Test
    void malformedJsonIs400Envelope() throws Exception {
        mockMvc.perform(post("/test/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    void unknownPathIs404Envelope() throws Exception {
        mockMvc.perform(get("/test/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void validationErrorIncludesFieldData() throws Exception {
        mockMvc.perform(post("/test/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").isNotEmpty());
    }

    @Test
    void acceptsClientTraceId() throws Exception {
        var result = mockMvc.perform(get("/test/probe/business")
                        .header("X-Request-Id", "abcdefgh"))
                .andReturn();
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo("abcdefgh");
        String bodyTraceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(bodyTraceId).isEqualTo("abcdefgh");
    }

    @Test
    void replacesInvalidClientTraceId() throws Exception {
        var result = mockMvc.perform(get("/test/probe/business")
                        .header("X-Request-Id", "bad!"))
                .andReturn();
        String trace = result.getResponse().getHeader("X-Trace-Id");
        assertThat(trace).isNotEqualTo("bad!").isNotBlank();
    }

    @Test
    void notAcceptableIs406Envelope() throws Exception {
        mockMvc.perform(get("/test/probe/status406"))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value(40601));
    }

    @Test
    void conflictIs409Envelope() throws Exception {
        mockMvc.perform(get("/test/probe/status409"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void unavailableIs503Envelope() throws Exception {
        mockMvc.perform(get("/test/probe/status503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50301));
    }
}
