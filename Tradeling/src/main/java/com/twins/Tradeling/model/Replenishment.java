package com.twins.Tradeling.model;

import jakarta.persistence.*;
import java.time.Instant;
import com.twins.Tradeling.model.DomainTypes.Warehouse;

@Entity
@Table(name = "replenishments", indexes = @Index(name = "idx_replenishment_product_date", columnList = "product_sku,moved_at"))
public class Replenishment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku", nullable = false, updatable = false)
    public Product product;

    @Column(nullable = false, unique = true, length = 80, updatable = false)
    public String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    public Warehouse fromWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    public Warehouse toWarehouse;

    @Column(nullable = false, updatable = false)
    public int quantity;

    @Column(nullable = false, length = 300, updatable = false)
    public String reason;

    @Column(nullable = false, updatable = false)
    public Instant movedAt;
}
