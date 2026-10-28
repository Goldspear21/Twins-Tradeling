package com.twins.Tradeling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void advisorUsesRuleBasedModeWhenAiIsDisabledAndStillFindsCrossBranchCandidates() throws Exception {
        mvc.perform(get("/api/v1/agent/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.provider").value("Ollama local"))
                .andExpect(jsonPath("$.writesInventory").value(false));

        mvc.perform(post("/api/v1/agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"MOU-BT-002","warehouse":"ABU_DHABI","scenario":"NORMAL",
                                 "externalSignals":"","useAi":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyDays").value(90))
                .andExpect(jsonPath("$.mode").value("RULE_BASED_AGENT"))
                .andExpect(jsonPath("$.demandForecasts.length()").value(1))
                .andExpect(jsonPath("$.demandForecasts[0].warehouse").value("ABU_DHABI"))
                .andExpect(jsonPath("$.transferRecommendations.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.transferRecommendations[0].toWarehouse").value("ABU_DHABI"));
    }

    @Test
    void chatUsesRuleBasedResponseWhenLocalModelIsDisabledForRequest() throws Exception {
        mvc.perform(post("/api/v1/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"How much stock is in the network?","useAi":false,
                                 "context":{"overview":{"skuCount":25,"totalStock":5000,"unitsSold30Days":80}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("RULE_BASED_CHAT"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("5000 units")))
                .andExpect(jsonPath("$.sources[0]").value("GET /api/v1/overview"));
    }

}
