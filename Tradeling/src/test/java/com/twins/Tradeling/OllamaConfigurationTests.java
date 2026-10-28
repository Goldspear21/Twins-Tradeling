package com.twins.Tradeling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ollama.model=qwen3:4b"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OllamaConfigurationTests {
    @Autowired MockMvc mvc;

    @Test
    void reportsLocalModelConfigurationWithoutCredentials() throws Exception {
        mvc.perform(get("/api/v1/agent/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.provider").value("Ollama local"))
                .andExpect(jsonPath("$.model").value("qwen3:4b"));
    }
}
