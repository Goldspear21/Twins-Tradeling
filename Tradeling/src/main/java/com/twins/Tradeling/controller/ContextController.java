package com.twins.Tradeling.controller;

import com.twins.Tradeling.api.ApiDtos.Overview;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Context", description = "Business definitions and stock summaries for human users and external AI clients.")
public class ContextController {
    private final InventoryService service;

    public ContextController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get stock and trailing-30-day sales summaries for all branches")
    public Overview overview() {
        return service.overview();
    }

    @GetMapping("/context")
    @Operation(summary = "Discover the business data contract", description = "Start here for warehouse enums, units, data semantics, API discovery and operation side effects. The full typed OpenAPI schema is at /api-docs.")
    public Map<String, Object> context() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("business", "Three-branch mobile accessories and computer peripherals retailer");
        result.put("apiVersion", "v1");
        result.put("currency", "AED");
        result.put("quantityUnit", "individual SKU units; integers");
        result.put("businessTimeZone", "Asia/Dubai");
        result.put("timestampFormat", "UTC ISO-8601; date-only planning values use UAE calendar dates");
        result.put("warehouseValues", Arrays.stream(Warehouse.values()).map(value -> Map.of("value", value.name(), "city", value.displayName)).toList());
        result.put("productFamilies", ProductFamily.values());
        result.put("disruptionTypes", DisruptionType.values());
        result.put("agentScenarios", AgentScenario.values());
        result.put("planStatuses", PlanStatus.values());
        result.put("openApiSchema", "/api-docs");
        result.put("interactiveApiExplorer", "/api-explorer");
        result.put("pagination", Map.of("page", "zero-based", "defaultSize", 50, "maximumSize", 200, "responseFields", List.of("content", "page", "size", "totalElements", "totalPages")));
        result.put("semantics", Map.of(
                "products", "Exactly three stock balances per SKU. New SKUs accept opening inventory; metadata updates cannot reset stock. Premade records come from data.sql; website submissions are persisted in MySQL.",
                "sales", "Each row is one SKU sale; quantity and actual unitPrice are recorded. totalPrice = quantity * unitPrice. Sales are append-only and deduct current branch stock.",
                "replenishments", "Each row is a completed source-to-destination transfer. Atomically moves stock without changing company-wide quantity; append-only.",
                "references", "Client-supplied unique reference per ledger. Duplicate posts return 409 without applying stock changes again. Query the corresponding ledger before retrying with another reference.",
                "lowStock", "Warehouse quantity <= product reorderPoint.",
                "dateFilters", "from is inclusive; to is exclusive. Use UTC ISO-8601 for sales and transfer queries.",
                "planning", "Rolling next 30 UAE dates, today through today+29. Baseline is prior 30 completed UAE dates: recorded SKU/warehouse units divided by 30. Adjust with demandChangePercent. No AI or market data integration.",
                "recommendation", "Ceiling of max(0, expected 30-day demand + reorderPoint - current stock - proposed inbound within the horizon). Arrival timing is represented by daily projections and shortage warnings.",
                "planExecution", "Preview saves nothing. Saved and CONFIRMED plans store proposals only; they never change inventory. Recalculations use live stock and sales, so they are not frozen historical forecasts.",
                "agent", "POST /api/v1/agent/analyze is read-only. It uses 90 days of sales, optional operator-supplied external signals, live stock, and estimated branch route costs to return demand forecasts, feasible transfer proposals, and before/after impact. It never executes a transfer."));
        result.put("operations", List.of(
                operation("GET", "/api/v1/products", false, false, "Search catalog and read stock"),
                operation("POST", "/api/v1/products", true, true, "Create SKU with opening stock"),
                operation("PUT", "/api/v1/products/{sku}", false, true, "Update catalog metadata only"),
                operation("GET", "/api/v1/sales", false, false, "Read sale ledger"),
                operation("POST", "/api/v1/sales", true, true, "Record sale and deduct stock"),
                operation("GET", "/api/v1/replenishments", false, false, "Read transfer ledger"),
                operation("POST", "/api/v1/replenishments", true, true, "Record completed warehouse transfer"),
                operation("GET", "/api/v1/plans", false, false, "Read saved proposals and live projections"),
                operation("POST", "/api/v1/plans/preview", false, false, "Preview a scenario without saving"),
                operation("POST", "/api/v1/plans", false, true, "Save proposal only"),
                operation("PUT", "/api/v1/plans/{id}", false, true, "Update proposal only"),
                operation("GET", "/api/v1/overview", false, false, "Read branch summaries"),
                operation("GET", "/api/v1/agent/status", false, false, "Check AI provider availability"),
                operation("POST", "/api/v1/agent/analyze", false, false, "90-day demand, route and impact advisor"),
                operation("POST", "/api/v1/agent/chat", false, false, "Ask questions over REST-fetched business data")));
        result.put("security", "No authentication is configured. Use locally or in a trusted development environment; add authentication, authorization and customer-data access controls before exposing this API.");
        return result;
    }

    private Map<String, Object> operation(String method, String path, boolean changesStock, boolean persistsData, String purpose) {
        return Map.of("method", method, "path", path, "changesStock", changesStock,
                "persistsData", persistsData, "purpose", purpose);
    }
}
