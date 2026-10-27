package com.twins.Tradeling.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.twins.Tradeling.model.DomainTypes.*;

@Entity
@Table(name = "planning_scenarios")
public class PlanningScenario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_sku", nullable = false)
    public Product product;

    @Column(nullable = false, length = 120)
    public String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    public DisruptionType disruptionType;

    @Column(nullable = false)
    public LocalDate startDate;

    @Column(nullable = false, precision = 7, scale = 2)
    public BigDecimal demandChangePercent;

    @Column(nullable = false)
    public int additionalLeadTimeDays;

    @Column(nullable = false)
    public int plannedInboundQuantity;

    public LocalDate inboundDate;

    @Column(nullable = false, length = 1000)
    public String actionNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public PlanStatus status;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = false)
    public Instant updatedAt;

    @Version
    public long version;
}
