package com.twins.Tradeling.controller;

import com.twins.Tradeling.api.ApiDtos.AgentAnalysis;
import com.twins.Tradeling.api.ApiDtos.AgentChatRequest;
import com.twins.Tradeling.api.ApiDtos.AgentChatResponse;
import com.twins.Tradeling.api.ApiDtos.AgentRequest;
import com.twins.Tradeling.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/agent", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "AI operations advisor", description = "Read-only agentic demand forecasting, transfer recommendations and business impact analysis.")
public class AgentController {
    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @Operation(summary = "Check AI provider status")
    public Map<String, Object> status() {
        return service.status();
    }

    @PostMapping("/analyze")
    @Operation(summary = "Analyze demand, feasible transfers and business impact",
            description = "Uses 90 days of recorded sales plus optional external signals. This endpoint is read-only and never executes a transfer.")
    public AgentAnalysis analyze(@Valid @RequestBody(required = false) AgentRequest request) {
        return service.analyze(request);
    }

    @PostMapping("/chat")
    @Operation(summary = "Ask a read-only question about live business data",
            description = "The client fetches relevant data from the application REST APIs and sends it as context. This endpoint never mutates inventory.")
    public AgentChatResponse chat(@Valid @RequestBody AgentChatRequest request) {
        return service.chat(request);
    }
}
