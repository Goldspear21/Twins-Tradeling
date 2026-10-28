package com.twins.Tradeling.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.POST;

class OllamaAiClientTests {
    private static final String ENDPOINT = "http://localhost:11434/api/chat";

    @Test
    void sendsRequestToLocalOllamaAndExtractsMessageContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("qwen3:4b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString(
                        "distinguish recorded facts from forecasts and proposals")))
                .andExpect(jsonPath("$.messages[1].content").value("Say hello"))
                .andRespond(withSuccess("""
                        {"message":{"role":"assistant","content":"Hello from local Ollama"},"done":true}
                        """, MediaType.APPLICATION_JSON));

        OllamaAiClient client = new OllamaAiClient(builder, "qwen3:4b", "http://localhost:11434");

        assertThat(client.generate("Say hello")).contains("Hello from local Ollama");
        server.verify();
    }

    @Test
    void mapsMissingLocalModelToPullGuidance() {
        assertProviderError(404, "ollama pull qwen3:4b");
    }

    @Test
    void reportsLocalModelServerFailuresWithoutCloudFallback() {
        assertProviderError(500, "available memory and the Ollama server");
    }

    private static void assertProviderError(int status, String reason) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.valueOf(status)));
        OllamaAiClient client = new OllamaAiClient(builder, "qwen3:4b", "http://localhost:11434");

        assertThatThrownBy(() -> client.generate("Hello"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(reason);
        server.verify();
    }
}
