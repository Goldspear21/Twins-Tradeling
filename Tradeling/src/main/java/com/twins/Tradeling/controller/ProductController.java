package com.twins.Tradeling.controller;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Products", description = "SKU catalog and live stock at the three warehouses. All monetary values are AED.")
public class ProductController {
    private final InventoryService service;

    public ProductController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search SKUs and stock", description = "q matches SKU or name. lowStock means quantity <= reorderPoint in the selected warehouse, or any warehouse if omitted. Pages are zero-based; size is 1..200. warehouse alone does not remove other warehouse inventory from responses.")
    public PageView<ProductView> list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) ProductFamily family,
                                     @RequestParam(required = false) Warehouse warehouse,
                                     @RequestParam(defaultValue = "false") boolean lowStock,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return service.products(q, family, warehouse, lowStock, page, size);
    }

    @GetMapping("/{sku}")
    @Operation(summary = "Get a SKU with warehouse-level inventory")
    public ProductView get(@PathVariable String sku) {
        return service.product(sku);
    }

    @PostMapping
    @Operation(summary = "Create a SKU with opening inventory", description = "Opening stock is set once at creation. Subsequent stock changes must be sales or completed replenishments, not catalog edits.")
    public ResponseEntity<ProductView> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.created(URI.create("/api/v1/products/" + request.sku())).body(service.createProduct(request));
    }

    @PutMapping("/{sku}")
    @Operation(summary = "Update catalog metadata only", description = "Cannot overwrite stock quantities or change SKU identity.")
    public ProductView update(@PathVariable String sku, @Valid @RequestBody ProductDetails request) {
        return service.updateProduct(sku, request);
    }
}
