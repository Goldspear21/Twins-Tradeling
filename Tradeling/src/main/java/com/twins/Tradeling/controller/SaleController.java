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
@RequestMapping(value = "/api/v1/sales", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Sales", description = "Append-only, per-SKU sales. Posting a sale deducts current warehouse stock.")
public class SaleController {
    private final InventoryService service;

    public SaleController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Query sales", description = "Optional SKU, warehouse, exact customer label, and UTC timestamps: from is inclusive; to is exclusive. Zero-based pagination, size 1..200.")
    public PageView<SaleView> list(@RequestParam(required = false) String sku,
                                  @RequestParam(required = false) Warehouse warehouse,
                                  @RequestParam(required = false) String customer,
                                  @RequestParam(required = false) Instant from,
                                  @RequestParam(required = false) Instant to,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        return service.sales(sku, warehouse, customer, from, to, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a sale record")
    public SaleView get(@PathVariable long id) {
        return service.sale(id);
    }

    @PostMapping
    @Operation(summary = "Record a completed sale and deduct stock", description = "Insufficient stock or a duplicate reference returns 409 and changes nothing. Records cannot be edited or deleted.")
    public ResponseEntity<SaleView> create(@Valid @RequestBody SaleRequest request) {
        SaleView sale = service.recordSale(request);
        return ResponseEntity.created(URI.create("/api/v1/sales/" + sale.id())).body(sale);
    }
}
