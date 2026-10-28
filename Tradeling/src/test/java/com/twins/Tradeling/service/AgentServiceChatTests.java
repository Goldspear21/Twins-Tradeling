package com.twins.Tradeling.service;

import com.twins.Tradeling.api.ApiDtos.AgentChatRequest;
import com.twins.Tradeling.api.ApiDtos.AgentChatResponse;
import com.twins.Tradeling.api.ApiDtos.AgentRequest;
import com.twins.Tradeling.model.DomainTypes.ProductFamily;
import com.twins.Tradeling.model.DomainTypes.Warehouse;
import com.twins.Tradeling.model.Product;
import com.twins.Tradeling.repository.ProductRepository;
import com.twins.Tradeling.repository.SaleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceChatTests {
    @Mock ProductRepository products;
    @Mock SaleRepository sales;
    @Mock OllamaAiClient ai;

    @Test
    void restockPromptUsesAiWithQuestionAndLiveContextInsteadOfScriptedAnswer() {
        when(ai.generate(anyString())).thenReturn(Optional.of("Generated answer"));
        AgentService service = new AgentService(products, sales, ai);

        AgentChatResponse response = service.chat(new AgentChatRequest(
                "What needs restocking?",
                Map.of("advisor", Map.of("demandForecasts", List.of(Map.of(
                        "sku", "MOU-BT-002", "risk", "HIGH", "currentStock", 4)),
                        "transferRecommendations", List.of(Map.of("quantity", 8)))),
                List.of(),
                true));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generate(prompt.capture());
        assertThat(prompt.getValue())
                .contains("What needs restocking?")
                .contains("MOU-BT-002")
                .contains("transferRecommendations")
                .contains("Treat forecasts as estimates")
                .contains("never imply an order, sale, or transfer was executed");
        assertThat(response.answer()).isEqualTo("Generated answer");
        assertThat(response.mode()).isEqualTo("OLLAMA_LOCAL_CHAT");
    }

    @Test
    void networkAnalysisPromptLabelsImpactAsModeledAndProposalsAsUnexecuted() {
        Product product = new Product("MOU-BT-002");
        product.name = "Bluetooth mouse";
        product.family = ProductFamily.MOUSE;
        product.unitPrice = new BigDecimal("25.00");
        product.reorderPoint = 8;
        product.abuDhabiStock = 4;
        product.dubaiStock = 20;
        product.sharjahStock = 15;
        product.supplierLeadTimeDays = 7;
        when(products.findAll()).thenReturn(List.of(product));
        when(sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(ai.generate(anyString())).thenReturn(Optional.of("Generated analysis"));
        AgentService service = new AgentService(products, sales, ai);

        service.analyze(new AgentRequest(null, null, null, "", true));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(ai).generate(prompt.capture());
        assertThat(prompt.getValue())
                .contains("These are model estimates, not observed outcomes")
                .contains("they do not mean stock has moved")
                .contains("Feasible transfer proposals (not executed)")
                .contains("Use AED for every currency amount")
                .contains("If the signal is none, do not imply a demand increase");
    }
}
