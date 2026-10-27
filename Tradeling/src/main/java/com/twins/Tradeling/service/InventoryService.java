package com.twins.Tradeling.service;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.*;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.repository.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    private final ProductRepository products;
    private final SaleRepository sales;
    private final ReplenishmentRepository transfers;
    private final PlanningScenarioRepository plans;

    public InventoryService(ProductRepository products, SaleRepository sales,
                            ReplenishmentRepository transfers, PlanningScenarioRepository plans) {
        this.products = products;
        this.sales = sales;
        this.transfers = transfers;
        this.plans = plans;
    }

    public PageView<ProductView> products(String search, ProductFamily family, Warehouse warehouse,
                                         boolean lowStock, int page, int size) {
        Specification<Product> spec = (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                conditions.add(builder.or(builder.like(builder.lower(root.get("sku")), term, '\\'),
                        builder.like(builder.lower(root.get("name")), term, '\\')));
            }
            if (family != null) {
                conditions.add(builder.equal(root.get("family"), family));
            }
            if (lowStock) {
                if (warehouse == null) {
                    conditions.add(builder.or(
                            builder.lessThanOrEqualTo(root.get("abuDhabiStock"), root.get("reorderPoint")),
                            builder.lessThanOrEqualTo(root.get("dubaiStock"), root.get("reorderPoint")),
                            builder.lessThanOrEqualTo(root.get("sharjahStock"), root.get("reorderPoint"))));
                } else {
                    String column = switch (warehouse) {
                        case ABU_DHABI -> "abuDhabiStock";
                        case DUBAI -> "dubaiStock";
                        case SHARJAH -> "sharjahStock";
                    };
                    conditions.add(builder.lessThanOrEqualTo(root.get(column), root.get("reorderPoint")));
                }
            }
            return builder.and(conditions.toArray(Predicate[]::new));
        };
        return PageView.of(products.findAll(spec, QuerySupport.page(page, size, Sort.by("sku"))).map(InventoryService::view));
    }

    public ProductView product(String sku) {
        return view(findProduct(sku, false));
    }

    @Transactional
    public ProductView createProduct(ProductCreateRequest request) {
        if (products.existsById(request.sku())) {
            throw conflict("SKU already exists");
        }
        Product product = new Product(request.sku());
        applyDetails(product, request.details());
        product.abuDhabiStock = request.inventory().abuDhabi();
        product.dubaiStock = request.inventory().dubai();
        product.sharjahStock = request.inventory().sharjah();
        return view(products.saveAndFlush(product));
    }

    @Transactional
    public ProductView updateProduct(String sku, ProductDetails details) {
        Product product = findProduct(sku, true);
        applyDetails(product, details);
        products.flush();
        return view(product);
    }

    private static void applyDetails(Product product, ProductDetails details) {
        product.name = details.name().trim();
        product.family = details.family();
        product.description = details.description().trim();
        product.brand = details.brand().trim();
        product.unitPrice = details.unitPrice().setScale(2);
        product.costPrice = details.costPrice().setScale(2);
        product.reorderPoint = details.reorderPoint();
        product.supplierLeadTimeDays = details.supplierLeadTimeDays();
    }

    public PageView<SaleView> sales(String sku, Warehouse warehouse, String customer, Instant from, Instant to, int page, int size) {
        return PageView.of(sales.findAll(QuerySupport.ledger(sku, warehouse, customer, from, to, "soldAt", false),
                QuerySupport.page(page, size, Sort.by(Sort.Order.desc("soldAt"), Sort.Order.desc("id")))).map(InventoryService::view));
    }

    public SaleView sale(long id) {
        return view(sales.findById(id).orElseThrow(() -> notFound("Sale not found")));
    }

    @Transactional
    public SaleView recordSale(SaleRequest request) {
        String reference = request.reference().trim();
        Product product = findProduct(request.sku(), true);
        if (sales.existsByReference(reference)) {
            throw conflict("Sale reference already exists; inventory was not changed");
        }
        if (product.stockAt(request.warehouse()) < request.quantity()) {
            throw conflict("Insufficient stock at " + request.warehouse());
        }
        Sale sale = new Sale();
        sale.product = product;
        sale.reference = reference;
        sale.customer = request.customer().trim();
        sale.warehouse = request.warehouse();
        sale.quantity = request.quantity();
        sale.unitPrice = request.unitPrice() == null ? product.unitPrice : request.unitPrice().setScale(2);
        sale.soldAt = request.soldAt() == null ? Instant.now() : request.soldAt();
        product.changeStock(sale.warehouse, -sale.quantity);
        return view(sales.saveAndFlush(sale));
    }

    public PageView<TransferView> transfers(String sku, Warehouse warehouse, Instant from, Instant to, int page, int size) {
        return PageView.of(transfers.findAll(QuerySupport.ledger(sku, warehouse, null, from, to, "movedAt", true),
                QuerySupport.page(page, size, Sort.by(Sort.Order.desc("movedAt"), Sort.Order.desc("id")))).map(InventoryService::view));
    }

    public TransferView transfer(long id) {
        return view(transfers.findById(id).orElseThrow(() -> notFound("Replenishment record not found")));
    }

    @Transactional
    public TransferView recordTransfer(TransferRequest request) {
        if (request.fromWarehouse() == request.toWarehouse()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination warehouses must differ");
        }
        Product product = findProduct(request.sku(), true);
        String reference = request.reference().trim();
        if (transfers.existsByReference(reference)) {
            throw conflict("Transfer reference already exists; inventory was not changed");
        }
        if (product.stockAt(request.fromWarehouse()) < request.quantity()) {
            throw conflict("Insufficient stock at " + request.fromWarehouse());
        }
        Replenishment transfer = new Replenishment();
        transfer.product = product;
        transfer.reference = reference;
        transfer.fromWarehouse = request.fromWarehouse();
        transfer.toWarehouse = request.toWarehouse();
        transfer.quantity = request.quantity();
        transfer.reason = request.reason().trim();
        transfer.movedAt = Instant.now();
        product.changeStock(transfer.fromWarehouse, -transfer.quantity);
        product.changeStock(transfer.toWarehouse, transfer.quantity);
        return view(transfers.saveAndFlush(transfer));
    }

    public Overview overview() {
        Instant now = Instant.now();
        List<Product> catalog = products.findAll(Sort.by("sku"));
        List<Sale> recent = sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(now.minus(Duration.ofDays(30)), now);
        List<WarehouseSummary> warehouses = Arrays.stream(Warehouse.values()).map(warehouse -> {
            List<Sale> branchSales = recent.stream().filter(sale -> sale.warehouse == warehouse).toList();
            return new WarehouseSummary(warehouse, warehouse.displayName,
                    catalog.stream().mapToLong(product -> product.stockAt(warehouse)).sum(),
                    branchSales.stream().mapToLong(sale -> sale.quantity).sum(),
                    branchSales.stream().map(sale -> sale.unitPrice.multiply(BigDecimal.valueOf(sale.quantity))).reduce(new BigDecimal("0.00"), BigDecimal::add),
                    catalog.stream().filter(product -> product.stockAt(warehouse) <= product.reorderPoint).count());
        }).toList();
        return new Overview(now, "AED", catalog.size(), warehouses.stream().mapToLong(WarehouseSummary::stockUnits).sum(),
                warehouses.stream().mapToLong(WarehouseSummary::unitsSold30Days).sum(),
                warehouses.stream().map(WarehouseSummary::revenue30Days).reduce(new BigDecimal("0.00"), BigDecimal::add),
                plans.count((root, query, builder) -> builder.notEqual(root.get("status"), PlanStatus.CLOSED)),
                warehouses, catalog.stream().filter(product -> Arrays.stream(Warehouse.values()).anyMatch(warehouse -> product.stockAt(warehouse) <= product.reorderPoint))
                        .map(InventoryService::view).toList());
    }

    public Product findProduct(String sku, boolean lock) {
        return (lock ? products.findBySkuForUpdate(sku) : products.findById(sku))
                .orElseThrow(() -> notFound("SKU not found: " + sku));
    }

    public static ProductView view(Product product) {
        List<StockView> inventory = Arrays.stream(Warehouse.values())
                .map(warehouse -> new StockView(warehouse, warehouse.displayName, product.stockAt(warehouse), product.reorderPoint,
                        product.stockAt(warehouse) <= product.reorderPoint)).toList();
        return new ProductView(product.sku, product.name, product.family, product.description, product.brand, "AED",
                product.unitPrice, product.costPrice, product.reorderPoint, product.supplierLeadTimeDays,
                inventory.stream().mapToLong(StockView::quantity).sum(), inventory, product.createdAt, product.version);
    }

    private static SaleView view(Sale sale) {
        return new SaleView(sale.id, sale.reference, sale.product.sku, sale.product.name, sale.customer, sale.warehouse,
                sale.quantity, "AED", sale.unitPrice, sale.unitPrice.multiply(BigDecimal.valueOf(sale.quantity)), sale.soldAt);
    }

    private static TransferView view(Replenishment transfer) {
        return new TransferView(transfer.id, transfer.reference, transfer.product.sku, transfer.product.name,
                transfer.fromWarehouse, transfer.toWarehouse, transfer.quantity, transfer.reason, transfer.movedAt);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
