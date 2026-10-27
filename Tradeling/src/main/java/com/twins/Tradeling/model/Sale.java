package com.twins.Tradeling.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.twins.Tradeling.model.DomainTypes.Warehouse;

@Entity
@Table(name = "sales", indexes = @Index(name = "idx_sales_product_warehouse_date", columnList = "product_sku,warehouse,sold_at"))
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku", nullable = false, updatable = false)
    public Product product;

    @Column(nullable = false, unique = true, length = 80, updatable = false)
    public String reference;

    @Column(nullable = false, length = 120, updatable = false)
    public String customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    public Warehouse warehouse;

    @Column(nullable = false, updatable = false)
    public int quantity;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    public BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    public Instant soldAt;
}
