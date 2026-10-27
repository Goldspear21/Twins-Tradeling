package com.twins.Tradeling.api;

import com.twins.Tradeling.model.DomainTypes.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class ApiDtos {
    private ApiDtos() {}

    public record InventoryInput(@PositiveOrZero int abuDhabi, @PositiveOrZero int dubai, @PositiveOrZero int sharjah) {}

    public record ProductDetails(
            @NotBlank @Size(max = 120) String name,
            @NotNull ProductFamily family,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 80) String brand,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal costPrice,
            @PositiveOrZero int reorderPoint,
            @Min(1) @Max(90) int supplierLeadTimeDays) {}

    public record ProductCreateRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{2,39}") String sku,
            @NotNull @Valid ProductDetails details,
            @NotNull @Valid InventoryInput inventory) {}

    public record StockView(Warehouse warehouse, String city, int quantity, int reorderPoint, boolean lowStock) {}

    public record ProductView(String sku, String name, ProductFamily family, String description, String brand,
                              String currency, BigDecimal unitPrice, BigDecimal costPrice,
                              int reorderPoint, int supplierLeadTimeDays, long totalStock,
                              List<StockView> inventory, Instant createdAt, long version) {}

    @Schema(description = "One SKU sale. Posting it deducts warehouse stock atomically. A reference must be unique; duplicates return 409 without deducting stock again.")
    public record SaleRequest(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 80) String reference,
            @NotBlank @Size(max = 120) String customer,
            @NotNull Warehouse warehouse,
            @Positive int quantity,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
            @Schema(description = "Actual unit price in AED; omit to use current catalog price.") BigDecimal unitPrice,
            @PastOrPresent @Schema(description = "UTC ISO-8601 sale timestamp; omit for the current time. Historical postings still deduct current stock.") Instant soldAt) {}

    public record SaleView(Long id, String reference, String sku, String productName, String customer,
                           Warehouse warehouse, int quantity, String currency, BigDecimal unitPrice,
                           BigDecimal totalPrice, Instant soldAt) {}

    @Schema(description = "A completed inter-warehouse move, not a proposal. Posting atomically deducts source stock and increases destination stock. Duplicate references return 409.")
    public record TransferRequest(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 80) String reference,
            @NotNull Warehouse fromWarehouse,
            @NotNull Warehouse toWarehouse,
            @Positive int quantity,
            @NotBlank @Size(max = 300) String reason) {}

    public record TransferView(Long id, String reference, String sku, String productName,
                               Warehouse fromWarehouse, Warehouse toWarehouse, int quantity,
                               String reason, Instant movedAt) {}

    @Schema(description = "A saved what-if scenario only: saving or confirming a plan NEVER changes inventory. Projections roll over the next 30 UAE calendar days using live stock and the last 30 completed days of recorded sales.")
    public record PlanRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 40) String sku,
            @NotNull Warehouse warehouse,
            @NotNull DisruptionType disruptionType,
            @NotNull @DecimalMin("-100") @DecimalMax("500") @Digits(integer = 3, fraction = 2)
            @Schema(description = "Percent change to baseline daily demand; 50 means a 50% increase.") BigDecimal demandChangePercent,
            @Min(0) @Max(60) int additionalLeadTimeDays,
            @PositiveOrZero int plannedInboundQuantity,
            @Schema(description = "Required when plannedInboundQuantity > 0. Arrival is at the start of this UAE date, within the next 30 days.") LocalDate inboundDate,
            @NotBlank @Size(max = 1000) String actionNote,
            @NotNull PlanStatus status) {}

    public record DailyProjection(LocalDate date, BigDecimal expectedDemand, int plannedInbound,
                                  BigDecimal projectedNetStock) {}

    public record PlanView(Long id, String title, String sku, String productName, Warehouse warehouse,
                           DisruptionType disruptionType, BigDecimal demandChangePercent,
                           int additionalLeadTimeDays, int plannedInboundQuantity, LocalDate inboundDate,
                           String actionNote, PlanStatus status, Instant createdAt, Instant updatedAt,
                           long version, Instant asOf, LocalDate horizonStart, LocalDate horizonEnd,
                           LocalDate baselineStart, LocalDate baselineEnd, long baselineUnitsSold,
                           BigDecimal baselineDailyDemand, BigDecimal adjustedDailyDemand, int startingStock,
                           long expectedDemand30Days, BigDecimal projectedEndingStock,
                           long recommendedReplenishmentQuantity, int effectiveLeadTimeDays,
                           LocalDate earliestSupplierArrival, LocalDate firstShortageDate,
                           List<String> warnings, List<DailyProjection> dailyProjection) {}

    public record WarehouseSummary(Warehouse warehouse, String city, long stockUnits, long unitsSold30Days,
                                   BigDecimal revenue30Days, long lowStockSkus) {}

    public record Overview(Instant asOf, String currency, long skuCount, long totalStock,
                           long unitsSold30Days, BigDecimal revenue30Days, long activeScenarios,
                           List<WarehouseSummary> warehouses, List<ProductView> lowStockProducts) {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> fieldErrors) {}
}
