package com.twins.Tradeling;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.repository.*;
import com.twins.Tradeling.service.InventoryService;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockApiIntegrationTests {
    private static final String SKU = "MOU-BT-002";
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired SaleRepository sales;
    @Autowired ReplenishmentRepository transfers;
    @Autowired PlanningScenarioRepository plans;
    @Autowired InventoryService inventory;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JsonMapper json;

    @Test
    void seedsTwentySkusInFiveFamiliesAndThreeWarehouses() throws Exception {
        assertThat(products.count()).isEqualTo(20);
        assertThat(products.findAll().stream().map(product -> product.family).distinct().count()).isEqualTo(5);
        assertThat(sales.count()).isEqualTo(600);
        assertThat(transfers.count()).isEqualTo(8);
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.content[0].currency").value("AED"))
                .andExpect(jsonPath("$.content[0].inventory.length()").value(3));
    }

    @Test
    void saleDeductsOnlySelectedWarehouseAndRetainsActualPrice() throws Exception {
        int before = stock(SKU, Warehouse.DUBAI);
        int otherBefore = stock(SKU, Warehouse.ABU_DHABI);
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"MOU-BT-002","reference":"TEST-SALE-1","customer":"Test customer",
                 "warehouse":"DUBAI","quantity":3,"unitPrice":70.50}
                """)).andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.totalPrice").value(211.50));
        assertThat(stock(SKU, Warehouse.DUBAI)).isEqualTo(before - 3);
        assertThat(stock(SKU, Warehouse.ABU_DHABI)).isEqualTo(otherBefore);
    }

    @Test
    void duplicateSaleReferenceDoesNotDeductAgain() throws Exception {
        String body = """
                {"sku":"MOU-BT-002","reference":"TEST-DUPLICATE","customer":"Test customer","warehouse":"DUBAI","quantity":2}
                """;
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        int afterFirst = stock(SKU, Warehouse.DUBAI);
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(stock(SKU, Warehouse.DUBAI)).isEqualTo(afterFirst);
    }

    @Test
    void insufficientStockDoesNotCreateLedgerRow() throws Exception {
        int before = stock(SKU, Warehouse.DUBAI);
        long rows = sales.count();
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"MOU-BT-002","reference":"TEST-EXCESS","customer":"Test customer","warehouse":"DUBAI","quantity":999999}
                """)).andExpect(status().isConflict());
        assertThat(stock(SKU, Warehouse.DUBAI)).isEqualTo(before);
        assertThat(sales.count()).isEqualTo(rows);
    }

    @Test
    void transfersConserveTotalStockAndDuplicateIsRejected() throws Exception {
        long total = inventory.product(SKU).totalStock();
        int source = stock(SKU, Warehouse.DUBAI);
        int destination = stock(SKU, Warehouse.SHARJAH);
        String body = """
                {"sku":"MOU-BT-002","reference":"TEST-TRANSFER","fromWarehouse":"DUBAI","toWarehouse":"SHARJAH","quantity":7,"reason":"Branch replenishment"}
                """;
        mvc.perform(post("/api/v1/replenishments").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertThat(stock(SKU, Warehouse.DUBAI)).isEqualTo(source - 7);
        assertThat(stock(SKU, Warehouse.SHARJAH)).isEqualTo(destination + 7);
        assertThat(inventory.product(SKU).totalStock()).isEqualTo(total);
        mvc.perform(post("/api/v1/replenishments").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat(stock(SKU, Warehouse.DUBAI)).isEqualTo(source - 7);
    }

    @Test
    void transferCannotUseSameWarehouseOrOversell() throws Exception {
        int before = stock(SKU, Warehouse.DUBAI);
        mvc.perform(post("/api/v1/replenishments").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"MOU-BT-002","reference":"TEST-SAME","fromWarehouse":"DUBAI","toWarehouse":"DUBAI","quantity":1,"reason":"Test"}
                """)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/replenishments").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"MOU-BT-002","reference":"TEST-TOO-MANY","fromWarehouse":"DUBAI","toWarehouse":"SHARJAH","quantity":999999,"reason":"Test"}
                """)).andExpect(status().isConflict());
        assertThat(stock(SKU, Warehouse.DUBAI)).isEqualTo(before);
    }

    @Test
    void filtersAndPaginationAreBounded() throws Exception {
        mvc.perform(get("/api/v1/products").param("family", "MOUSE").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.content.length()").value(2));
        mvc.perform(get("/api/v1/products").param("size", "201")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/sales").param("sku", SKU).param("warehouse", "DUBAI"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(10));
        mvc.perform(get("/api/v1/sales").param("from", "2026-10-20T00:00:00Z").param("to", "2026-10-19T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidInputsReturnStructuredErrors() throws Exception {
        mvc.perform(post("/api/v1/sales").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku":"MOU-BT-002","reference":"TEST-INVALID","customer":"","warehouse":"DUBAI","quantity":-1}
                """)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.quantity").exists())
                .andExpect(jsonPath("$.fieldErrors.customer").exists());
        mvc.perform(get("/api/v1/sales").param("warehouse", "UNKNOWN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/products/MISSING-SKU")).andExpect(status().isNotFound());
    }

    @Test
    void metadataUpdateCannotChangeInventory() throws Exception {
        long before = inventory.product(SKU).totalStock();
        mvc.perform(put("/api/v1/products/" + SKU).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Updated mouse","family":"MOUSE","description":"Updated specification","brand":"Test brand",
                 "unitPrice":80.00,"costPrice":40.00,"reorderPoint":25,"supplierLeadTimeDays":8}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated mouse"));
        assertThat(inventory.product(SKU).totalStock()).isEqualTo(before);
    }

    @Test
    void previewHasThirtyDaysAndDoesNotPersistOrMutateStock() throws Exception {
        long count = plans.count();
        long stock = inventory.product(SKU).totalStock();
        String result = mvc.perform(post("/api/v1/plans/preview").contentType(MediaType.APPLICATION_JSON).content(planBody(0, null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyProjection.length()").value(30))
                .andExpect(jsonPath("$.id").isEmpty()).andReturn().getResponse().getContentAsString();
        var view = json.readTree(result);
        LocalDate start = LocalDate.parse(view.get("horizonStart").asString());
        assertThat(LocalDate.parse(view.get("horizonEnd").asString())).isEqualTo(start.plusDays(29));
        BigDecimal expectedDemand = BigDecimal.valueOf(view.get("baselineUnitsSold").asLong()).multiply(new BigDecimal("1.50"));
        assertThat(view.get("expectedDemand30Days").asLong()).isEqualTo(expectedDemand.setScale(0, java.math.RoundingMode.CEILING).longValueExact());
        assertThat(new BigDecimal(view.get("projectedEndingStock").asString()))
                .isEqualByComparingTo(BigDecimal.valueOf(view.get("startingStock").asInt()).subtract(expectedDemand));
        assertThat(plans.count()).isEqualTo(count);
        assertThat(inventory.product(SKU).totalStock()).isEqualTo(stock);
    }

    @Test
    void confirmingPlanStillDoesNotMoveStockAndCanBeUpdated() throws Exception {
        long stock = inventory.product(SKU).totalStock();
        String body = planBody(20, LocalDate.now(ZoneId.of("Asia/Dubai")).plusDays(3));
        String result = mvc.perform(post("/api/v1/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(result).get("id").asLong();
        mvc.perform(put("/api/v1/plans/" + id).contentType(MediaType.APPLICATION_JSON).content(body.replace("CONFIRMED", "CLOSED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(inventory.product(SKU).totalStock()).isEqualTo(stock);
    }

    @Test
    void rejectsInboundOutsideHorizonAndInvalidDemand() throws Exception {
        mvc.perform(post("/api/v1/plans/preview").contentType(MediaType.APPLICATION_JSON)
                .content(planBody(20, LocalDate.now(ZoneId.of("Asia/Dubai")).plusDays(30)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/plans/preview").contentType(MediaType.APPLICATION_JSON)
                .content(planBody(0, null).replace("50.00", "-101.00"))).andExpect(status().isBadRequest());
    }

    @Test
    void contextAndOpenApiPublishSchemasAndSideEffects() throws Exception {
        mvc.perform(get("/api/v1/context")).andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseValues.length()").value(3)).andExpect(jsonPath("$.openApiSchema").value("/api-docs"));
        mvc.perform(get("/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/plans/preview'].post").exists())
                .andExpect(jsonPath("$.components.schemas.SaleRequest").exists());
        mvc.perform(get("/api/v1/overview")).andExpect(status().isOk()).andExpect(jsonPath("$.warehouses.length()").value(3));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSalesCannotOversell() throws Exception {
        String sku = "TEST-RACE-001";
        inventory.createProduct(new ProductCreateRequest(sku,
                new ProductDetails("Race test", ProductFamily.MOUSE, "Test", "Test", new BigDecimal("10.00"),
                        new BigDecimal("5.00"), 1, 5), new InventoryInput(0, 5, 0)));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(executor.submit(() -> raceSale(sku, "RACE-A", start)),
                    executor.submit(() -> raceSale(sku, "RACE-B", start)));
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) succeeded++;
            assertThat(succeeded).isEqualTo(1);
            assertThat(stock(sku, Warehouse.DUBAI)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                sales.deleteAll(sales.findAll((root, query, builder) -> builder.equal(root.get("product").get("sku"), sku)));
                products.deleteById(sku);
            });
        }
    }

    private boolean raceSale(String sku, String reference, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            inventory.recordSale(new SaleRequest(sku, reference, "Test customer", Warehouse.DUBAI, 4, null, null));
            return true;
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode().value()).isEqualTo(409);
            return false;
        }
    }

    private int stock(String sku, Warehouse warehouse) {
        return products.findById(sku).orElseThrow().stockAt(warehouse);
    }

    private String planBody(int inbound, LocalDate date) {
        return """
                {"title":"Test scenario","sku":"MOU-BT-002","warehouse":"ABU_DHABI","disruptionType":"DEMAND_SURGE",
                 "demandChangePercent":50.00,"additionalLeadTimeDays":5,"plannedInboundQuantity":%d,"inboundDate":%s,
                 "actionNote":"Review a branch transfer","status":"CONFIRMED"}
                """.formatted(inbound, date == null ? "null" : "\"" + date + "\"");
    }
}
