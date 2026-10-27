package com.twins.Tradeling.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.twins.Tradeling.model.DomainTypes.ProductFamily;
import com.twins.Tradeling.model.DomainTypes.Warehouse;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @Column(length = 40, nullable = false, updatable = false)
    public String sku;

    @Column(nullable = false, length = 120)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public ProductFamily family;

    @Column(nullable = false, length = 500)
    public String description;

    @Column(nullable = false, length = 80)
    public String brand;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal costPrice;

    @Column(nullable = false)
    public int abuDhabiStock;

    @Column(nullable = false)
    public int dubaiStock;

    @Column(nullable = false)
    public int sharjahStock;

    @Column(nullable = false)
    public int reorderPoint;

    @Column(nullable = false)
    public int supplierLeadTimeDays;

    @Column(nullable = false)
    public Instant createdAt;

    @Version
    public long version;

    protected Product() {}

    public Product(String sku) {
        this.sku = sku;
        this.createdAt = Instant.now();
    }

    public int stockAt(Warehouse warehouse) {
        return switch (warehouse) {
            case ABU_DHABI -> abuDhabiStock;
            case DUBAI -> dubaiStock;
            case SHARJAH -> sharjahStock;
        };
    }

    public void changeStock(Warehouse warehouse, int delta) {
        int stock = Math.addExact(stockAt(warehouse), delta);
        if (stock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        switch (warehouse) {
            case ABU_DHABI -> abuDhabiStock = stock;
            case DUBAI -> dubaiStock = stock;
            case SHARJAH -> sharjahStock = stock;
        }
    }
}
