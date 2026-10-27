package com.twins.Tradeling.controller;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.service.PlanningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/plans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Planning", description = "Saved, editable market-disruption scenarios for the next 30 days. Proposals never mutate stock; projections are recalculated from live data, not frozen forecasts.")
public class PlanningController {
    private final PlanningService service;

    public PlanningController(PlanningService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List saved scenarios and rolling projections", description = "Filter by SKU, warehouse or status. Zero-based pagination, size 1..200. Each scenario includes its baseline window, calculation assumptions and exactly 30 daily projections.")
    public PageView<PlanView> list(@RequestParam(required = false) String sku,
                                  @RequestParam(required = false) Warehouse warehouse,
                                  @RequestParam(required = false) PlanStatus status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return service.list(sku, warehouse, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a saved scenario with its current projection")
    public PlanView get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview a disruption scenario without saving", description = "Does not persist anything and does not change stock. Uses the same transparent calculation as saved plans.")
    public PlanView preview(@Valid @RequestBody PlanRequest request) {
        return service.preview(request);
    }

    @PostMapping
    @Operation(summary = "Save a what-if scenario", description = "Only stores assumptions and proposed actions; no stock mutation, even for CONFIRMED status.")
    public ResponseEntity<PlanView> create(@Valid @RequestBody PlanRequest request) {
        PlanView plan = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/plans/" + plan.id())).body(plan);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update scenario assumptions or status", description = "CONFIRMED is a planning label, not permission to execute inventory movements. To actually move stock, separately post a completed replenishment.")
    public PlanView update(@PathVariable long id, @Valid @RequestBody PlanRequest request) {
        return service.update(id, request);
    }
}
