package com.twins.Tradeling.controller;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.DomainTypes.Warehouse;
import com.twins.Tradeling.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/replenishments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Replenishments", description = "Completed warehouse-to-warehouse transfers. These are real inventory mutations, unlike planning scenarios.")
public class ReplenishmentController {
    private final InventoryService service;

    public ReplenishmentController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Query warehouse movements", description = "warehouse matches either source or destination. from is inclusive and to exclusive, using UTC ISO-8601 timestamps. Zero-based pagination, size 1..200.")
    public PageView<TransferView> list(@RequestParam(required = false) String sku,
                                      @RequestParam(required = false) Warehouse warehouse,
                                      @RequestParam(required = false) Instant from,
                                      @RequestParam(required = false) Instant to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return service.transfers(sku, warehouse, from, to, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a completed warehouse movement")
    public TransferView get(@PathVariable long id) {
        return service.transfer(id);
    }

    @PostMapping
    @Operation(summary = "Record a completed warehouse transfer", description = "Atomically decreases source inventory and increases destination inventory. The total company stock remains unchanged. Duplicate references or insufficient source stock return 409; identical warehouses return 400.")
    public ResponseEntity<TransferView> create(@Valid @RequestBody TransferRequest request) {
        TransferView transfer = service.recordTransfer(request);
        return ResponseEntity.created(URI.create("/api/v1/replenishments/" + transfer.id())).body(transfer);
    }
}
