package com.twins.Tradeling.config;

import com.twins.Tradeling.model.*;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner {
    private final ProductRepository products;
    private final SaleRepository sales;
    private final ReplenishmentRepository transfers;
    private final PlanningScenarioRepository plans;

    public DemoDataInitializer(ProductRepository products, SaleRepository sales,
                               ReplenishmentRepository transfers, PlanningScenarioRepository plans) {
        this.products = products;
        this.sales = sales;
        this.transfers = transfers;
        this.plans = plans;
    }

    private record Seed(String sku, ProductFamily family, String name, String description, String price, int leadTime) {}

    @Override
    @Transactional
    public void run(String... args) {
        if (products.count() != 0) {
            return;
        }
        List<Seed> catalog = List.of(
                new Seed("MOU-WIRED-001", ProductFamily.MOUSE, "Essential wired mouse", "USB-A, optical 1200 DPI, three buttons", "29.00", 5),
                new Seed("MOU-BT-002", ProductFamily.MOUSE, "Bluetooth travel mouse", "Bluetooth 5.2, silent clicks, rechargeable", "79.00", 7),
                new Seed("MOU-24G-003", ProductFamily.MOUSE, "Wireless desktop mouse", "2.4 GHz USB receiver, ergonomic grip", "59.00", 6),
                new Seed("MOU-GAME-004", ProductFamily.MOUSE, "RGB gaming mouse", "Wired USB, 7200 DPI, six programmable buttons", "119.00", 10),
                new Seed("KEY-WIRED-001", ProductFamily.KEYBOARD, "Full-size wired keyboard", "USB-A, full-size layout, spill-resistant", "49.00", 5),
                new Seed("KEY-BT-002", ProductFamily.KEYBOARD, "Compact Bluetooth keyboard", "Multi-device Bluetooth, compact 78-key layout", "129.00", 8),
                new Seed("KEY-MECH-003", ProductFamily.KEYBOARD, "Mechanical gaming keyboard", "USB-C, tactile switches, RGB backlight", "219.00", 12),
                new Seed("KEY-COMBO-004", ProductFamily.KEYBOARD, "Wireless keyboard and mouse kit", "Full-size keyboard, mouse and shared USB receiver", "149.00", 9),
                new Seed("CHG-20W-001", ProductFamily.CHARGER, "20 W USB-C wall charger", "USB Power Delivery, single USB-C port, UAE plug", "45.00", 6),
                new Seed("CHG-30W-002", ProductFamily.CHARGER, "30 W compact GaN charger", "GaN, USB-C PD, foldable UAE plug", "75.00", 8),
                new Seed("CHG-65W-003", ProductFamily.CHARGER, "65 W dual-port GaN charger", "Laptop-capable PD, USB-C and USB-A ports", "149.00", 12),
                new Seed("CHG-CAR-004", ProductFamily.CHARGER, "Dual-port car charger", "12/24 V socket, USB-C PD and USB-A", "55.00", 7),
                new Seed("CBL-CC-001", ProductFamily.CABLE, "USB-C to USB-C cable", "1 m braided cable, 60 W PD charging", "25.00", 4),
                new Seed("CBL-AC-002", ProductFamily.CABLE, "USB-A to USB-C cable", "1.5 m cable, charging and USB 2.0 data", "19.00", 4),
                new Seed("CBL-LTG-003", ProductFamily.CABLE, "USB-C to Lightning cable", "1 m cable for compatible Lightning devices", "49.00", 9),
                new Seed("CBL-HDMI-004", ProductFamily.CABLE, "4K HDMI cable", "2 m HDMI 2.0 cable, 4K at 60 Hz", "35.00", 5),
                new Seed("HUB-4USB-001", ProductFamily.HUB, "Four-port USB-A hub", "USB 3.0, four USB-A data ports", "55.00", 6),
                new Seed("HUB-6IN1-002", ProductFamily.HUB, "Six-in-one USB-C hub", "HDMI, USB-A, SD reader and USB-C PD passthrough", "139.00", 10),
                new Seed("HUB-8IN1-003", ProductFamily.HUB, "Eight-in-one USB-C dock", "HDMI, Ethernet, SD, USB-A and 100 W PD passthrough", "239.00", 14),
                new Seed("HUB-ETH-004", ProductFamily.HUB, "USB-C Ethernet adapter", "Gigabit Ethernet for USB-C laptops", "89.00", 8)
        );
        Instant now = Instant.now();
        List<Product> saved = new ArrayList<>();
        for (int i = 0; i < catalog.size(); i++) {
            Seed seed = catalog.get(i);
            Product product = new Product(seed.sku());
            product.name = seed.name();
            product.family = seed.family();
            product.description = seed.description();
            product.brand = "Nexa Demo";
            product.unitPrice = new BigDecimal(seed.price());
            product.costPrice = product.unitPrice.multiply(new BigDecimal("0.55")).setScale(2, RoundingMode.HALF_UP);
            product.reorderPoint = 20;
            product.supplierLeadTimeDays = seed.leadTime();
            product.abuDhabiStock = i % 4 == 0 ? 35 : 90 + i * 3;
            product.dubaiStock = 120 + i * 4;
            product.sharjahStock = i % 5 == 0 ? 38 : 65 + i * 2;
            product.createdAt = now.minus(Duration.ofDays(45));
            products.save(product);
            saved.add(product);
            for (Warehouse warehouse : Warehouse.values()) {
                for (int sample = 0; sample < 10; sample++) {
                    Sale sale = new Sale();
                    sale.product = product;
                    sale.reference = "DEMO-SALE-" + i + "-" + warehouse + "-" + sample;
                    sale.customer = "Demo customer " + (1 + (i + sample) % 12);
                    sale.warehouse = warehouse;
                    sale.quantity = 1 + (i + warehouse.ordinal() + sample) % 3;
                    sale.unitPrice = product.unitPrice;
                    sale.soldAt = now.minus(Duration.ofDays(1 + sample * 3)).minusSeconds(i * 60L);
                    product.changeStock(warehouse, -sale.quantity);
                    sales.save(sale);
                }
            }
        }
        for (int i = 1; i <= 8; i++) {
            Product product = saved.get(i);
            Replenishment transfer = new Replenishment();
            transfer.product = product;
            transfer.reference = "DEMO-MOVE-" + i;
            transfer.fromWarehouse = Warehouse.DUBAI;
            transfer.toWarehouse = i % 2 == 0 ? Warehouse.SHARJAH : Warehouse.ABU_DHABI;
            transfer.quantity = 5 + i;
            transfer.reason = "Demo branch replenishment";
            transfer.movedAt = now.minus(Duration.ofHours(i * 4L));
            product.changeStock(transfer.fromWarehouse, -transfer.quantity);
            product.changeStock(transfer.toWarehouse, transfer.quantity);
            transfers.save(transfer);
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Dubai"));
        for (int i = 0; i < 3; i++) {
            PlanningScenario plan = new PlanningScenario();
            plan.product = saved.get(i * 4);
            plan.title = List.of("Mouse supplier delay", "Keyboard promotion demand", "Charger delivery disruption").get(i);
            plan.warehouse = Warehouse.ABU_DHABI;
            plan.disruptionType = DisruptionType.values()[i];
            plan.startDate = today;
            plan.demandChangePercent = new BigDecimal(i == 1 ? "80.00" : "25.00");
            plan.additionalLeadTimeDays = i == 1 ? 0 : 5;
            plan.plannedInboundQuantity = i == 1 ? 0 : 30;
            plan.inboundDate = i == 1 ? null : today.plusDays(12);
            plan.actionNote = "Demo proposal: review excess stock in Dubai before committing a replenishment.";
            plan.status = PlanStatus.DRAFT;
            plan.createdAt = now;
            plan.updatedAt = now;
            plans.save(plan);
        }
    }
}
