package com.twins.Tradeling.service;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.*;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PlanningService {
    private static final ZoneId UAE = ZoneId.of("Asia/Dubai");
    private final PlanningScenarioRepository plans;
    private final SaleRepository sales;
    private final InventoryService inventory;

    public PlanningService(PlanningScenarioRepository plans, SaleRepository sales, InventoryService inventory) {
        this.plans = plans;
        this.sales = sales;
        this.inventory = inventory;
    }

    public PageView<PlanView> list(String sku, Warehouse warehouse, PlanStatus status, int page, int size) {
        return PageView.of(plans.findAll((root, query, builder) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (sku != null && !sku.isBlank()) predicates.add(builder.equal(root.get("product").get("sku"), sku.trim()));
            if (warehouse != null) predicates.add(builder.equal(root.get("warehouse"), warehouse));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, QuerySupport.page(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")))).map(this::project));
    }

    public PlanView get(long id) {
        return project(find(id));
    }

    public PlanView preview(PlanRequest request) {
        PlanningScenario plan = new PlanningScenario();
        apply(plan, request);
        return project(plan);
    }

    @Transactional
    public PlanView create(PlanRequest request) {
        PlanningScenario plan = new PlanningScenario();
        apply(plan, request);
        plan.createdAt = Instant.now();
        return project(plans.saveAndFlush(plan));
    }

    @Transactional
    public PlanView update(long id, PlanRequest request) {
        PlanningScenario plan = find(id);
        apply(plan, request);
        plans.flush();
        return project(plan);
    }

    private PlanningScenario find(long id) {
        return plans.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning scenario not found"));
    }

    private void apply(PlanningScenario plan, PlanRequest request) {
        LocalDate today = LocalDate.now(UAE);
        if (request.plannedInboundQuantity() > 0 && (request.inboundDate() == null
                || request.inboundDate().isBefore(today) || request.inboundDate().isAfter(today.plusDays(29)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inbound date is required for planned inbound stock and must be within the next 30 UAE calendar days");
        }
        if (request.plannedInboundQuantity() == 0 && request.inboundDate() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Omit inboundDate when plannedInboundQuantity is zero");
        }
        plan.product = inventory.findProduct(request.sku(), false);
        plan.title = request.title().trim();
        plan.warehouse = request.warehouse();
        plan.disruptionType = request.disruptionType();
        plan.startDate = today;
        plan.demandChangePercent = request.demandChangePercent().setScale(2);
        plan.additionalLeadTimeDays = request.additionalLeadTimeDays();
        plan.plannedInboundQuantity = request.plannedInboundQuantity();
        plan.inboundDate = request.inboundDate();
        plan.actionNote = request.actionNote().trim();
        plan.status = request.status();
        plan.updatedAt = Instant.now();
    }

    private PlanView project(PlanningScenario plan) {
        Instant now = Instant.now();
        LocalDate today = now.atZone(UAE).toLocalDate();
        LocalDate end = today.plusDays(29);
        LocalDate baselineStart = today.minusDays(30);
        long baselineUnits = sales.quantitySold(plan.product.sku, plan.warehouse,
                baselineStart.atStartOfDay(UAE).toInstant(), today.atStartOfDay(UAE).toInstant());
        BigDecimal factor = BigDecimal.ONE.add(plan.demandChangePercent.movePointLeft(2));
        BigDecimal demand30 = BigDecimal.valueOf(baselineUnits).multiply(factor);
        BigDecimal baselineDaily = BigDecimal.valueOf(baselineUnits).divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
        BigDecimal adjustedDaily = demand30.divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
        int startingStock = plan.product.stockAt(plan.warehouse);
        int effectiveLeadTime = plan.product.supplierLeadTimeDays + plan.additionalLeadTimeDays;
        LocalDate earliestArrival = today.plusDays(effectiveLeadTime);
        List<String> warnings = new ArrayList<>();
        if (baselineUnits == 0) {
            warnings.add("No sales recorded for this SKU and warehouse in the baseline window; zero demand is not evidence of no future demand.");
        }
        boolean inboundInHorizon = plan.inboundDate != null && !plan.inboundDate.isBefore(today) && !plan.inboundDate.isAfter(end);
        int includedInbound = inboundInHorizon ? plan.plannedInboundQuantity : 0;
        if (plan.plannedInboundQuantity > 0 && !inboundInHorizon) {
            warnings.add("The saved inbound date is outside the current rolling horizon and is excluded; update the scenario. A proposal is never assumed to have been received.");
        }
        if (includedInbound > 0 && plan.inboundDate.isBefore(earliestArrival)) {
            warnings.add("Proposed inbound arrives before the disrupted supplier lead time allows; verify an alternative source or warehouse transfer.");
        }
        List<DailyProjection> daily = new ArrayList<>();
        int cumulativeInbound = 0;
        LocalDate firstShortage = null;
        BigDecimal endingStock = BigDecimal.valueOf(startingStock);
        for (int day = 0; day < 30; day++) {
            LocalDate date = today.plusDays(day);
            int inbound = inboundInHorizon && date.equals(plan.inboundDate) ? includedInbound : 0;
            cumulativeInbound += inbound;
            BigDecimal cumulativeDemand = demand30.multiply(BigDecimal.valueOf(day + 1)).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);
            endingStock = BigDecimal.valueOf((long) startingStock + cumulativeInbound).subtract(cumulativeDemand);
            if (firstShortage == null && endingStock.signum() < 0) {
                firstShortage = date;
            }
            daily.add(new DailyProjection(date, adjustedDaily, inbound, endingStock.setScale(4, RoundingMode.HALF_UP)));
        }
        long recommended = demand30.add(BigDecimal.valueOf(plan.product.reorderPoint))
                .subtract(BigDecimal.valueOf((long) startingStock + includedInbound)).max(BigDecimal.ZERO)
                .setScale(0, RoundingMode.CEILING).longValueExact();
        if (firstShortage != null) {
            warnings.add("Projected demand exceeds available stock on " + firstShortage + "; negative projected stock represents unmet demand, not actual inventory.");
            if (earliestArrival.isAfter(firstShortage)) {
                warnings.add("Supplier replenishment may arrive after the first shortage; review available stock at the other branches.");
            }
        }
        warnings.add("What-if arithmetic, not an AI forecast. Baseline is recorded units / 30, adjusted by the supplied demand percentage; inbound arrives at the start of its specified day. Saving or confirming never executes a sale, purchase, or transfer.");
        return new PlanView(plan.id, plan.title, plan.product.sku, plan.product.name, plan.warehouse, plan.disruptionType,
                plan.demandChangePercent, plan.additionalLeadTimeDays, plan.plannedInboundQuantity, plan.inboundDate,
                plan.actionNote, plan.status, plan.createdAt, plan.updatedAt, plan.version, now, today, end,
                baselineStart, today.minusDays(1), baselineUnits, baselineDaily, adjustedDaily, startingStock,
                demand30.setScale(0, RoundingMode.CEILING).longValueExact(), endingStock.setScale(4, RoundingMode.HALF_UP),
                recommended, effectiveLeadTime, earliestArrival, firstShortage, warnings, daily);
    }
}
