package com.twins.Tradeling.service;

import com.twins.Tradeling.api.ApiDtos.*;
import com.twins.Tradeling.model.Product;
import com.twins.Tradeling.model.Sale;
import com.twins.Tradeling.model.DomainTypes.*;
import com.twins.Tradeling.repository.ProductRepository;
import com.twins.Tradeling.repository.SaleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AgentService {
    private static final ZoneId UAE = ZoneId.of("Asia/Dubai");
    private static final int HISTORY_DAYS = 90;
    private static final int FORECAST_DAYS = 30;
    private static final Pattern PERCENT = Pattern.compile("([+-]?\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern SKU = Pattern.compile("\\b[A-Z]{2,6}-[A-Z0-9-]{3,30}\\b");
    private static final Map<String, Integer> ROUTE_DAYS = Map.of(
            route(Warehouse.ABU_DHABI, Warehouse.DUBAI), 1,
            route(Warehouse.DUBAI, Warehouse.ABU_DHABI), 1,
            route(Warehouse.ABU_DHABI, Warehouse.SHARJAH), 1,
            route(Warehouse.SHARJAH, Warehouse.ABU_DHABI), 1,
            route(Warehouse.DUBAI, Warehouse.SHARJAH), 1,
            route(Warehouse.SHARJAH, Warehouse.DUBAI), 1);
    private static final Map<String, BigDecimal> ROUTE_COST = Map.of(
            route(Warehouse.ABU_DHABI, Warehouse.DUBAI), new BigDecimal("55.00"),
            route(Warehouse.DUBAI, Warehouse.ABU_DHABI), new BigDecimal("55.00"),
            route(Warehouse.ABU_DHABI, Warehouse.SHARJAH), new BigDecimal("40.00"),
            route(Warehouse.SHARJAH, Warehouse.ABU_DHABI), new BigDecimal("40.00"),
            route(Warehouse.DUBAI, Warehouse.SHARJAH), new BigDecimal("35.00"),
            route(Warehouse.SHARJAH, Warehouse.DUBAI), new BigDecimal("35.00"));

    private final ProductRepository products;
    private final SaleRepository sales;
    private final OllamaAiClient ai;
    private final ObjectMapper json = new ObjectMapper();

    public AgentService(ProductRepository products, SaleRepository sales, OllamaAiClient ai) {
        this.products = products;
        this.sales = sales;
        this.ai = ai;
    }

    public AgentAnalysis analyze(AgentRequest request) {
        AgentRequest safe = request == null ? new AgentRequest(null, null, AgentScenario.NORMAL, "", true) : request;
        AgentScenario scenario = safe.scenario() == null ? AgentScenario.NORMAL : safe.scenario();
        String signals = safe.externalSignals() == null ? "" : safe.externalSignals().trim();
        Instant now = Instant.now();
        LocalDate today = now.atZone(UAE).toLocalDate();
        Instant from = today.minusDays(HISTORY_DAYS).atStartOfDay(UAE).toInstant();
        Instant recentFrom = today.minusDays(30).atStartOfDay(UAE).toInstant();
        Instant priorFrom = today.minusDays(HISTORY_DAYS).atStartOfDay(UAE).toInstant();
        List<Product> catalog = products.findAll().stream()
                .filter(product -> safe.sku() == null || safe.sku().isBlank() || product.sku.equalsIgnoreCase(safe.sku().trim()))
                .sorted(Comparator.comparing(product -> product.sku))
                .toList();
        List<Sale> history = sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(from, now);
        Map<String, Map<Warehouse, Long>> sold90 = quantities(history, from, now);
        Map<String, Map<Warehouse, Long>> sold30 = quantities(history, recentFrom, now);
        Map<String, Map<Warehouse, Long>> soldPrior60 = quantities(history, priorFrom, recentFrom);
        BigDecimal signalAdjustment = signalAdjustment(signals);
        boolean signalHasSkuScope = safe.sku() != null && !safe.sku().isBlank();
        List<DemandForecast> forecasts = new ArrayList<>();
        Map<String, DemandForecast> byKey = new HashMap<>();
        for (Product product : catalog) {
            for (Warehouse warehouse : Warehouse.values()) {
                long units90 = get(sold90, product.sku, warehouse);
                long units30 = get(sold30, product.sku, warehouse);
                long unitsPrior60 = get(soldPrior60, product.sku, warehouse);
                BigDecimal avg90 = perDay(units90, HISTORY_DAYS);
                BigDecimal avg30 = perDay(units30, 30);
                BigDecimal priorDaily = perDay(unitsPrior60, 60);
                BigDecimal trend = priorDaily.signum() == 0
                        ? (avg30.signum() == 0 ? BigDecimal.ZERO : new BigDecimal("100.00"))
                        : avg30.subtract(priorDaily).divide(priorDaily, 4, RoundingMode.HALF_UP).movePointRight(2);
                boolean signalApplies = signalHasSkuScope && product.sku.equalsIgnoreCase(safe.sku().trim());
                BigDecimal appliedSignalAdjustment = signalApplies ? signalAdjustment : BigDecimal.ZERO;
                BigDecimal daily = avg90.multiply(new BigDecimal("0.40"))
                        .add(avg30.multiply(new BigDecimal("0.60")))
                        .multiply(BigDecimal.ONE.add(appliedSignalAdjustment.movePointLeft(2)))
                        .max(BigDecimal.ZERO);
                long forecast = daily.multiply(BigDecimal.valueOf(FORECAST_DAYS)).setScale(0, RoundingMode.CEILING).longValue();
                int stock = product.stockAt(warehouse);
                int coverage = daily.signum() == 0 ? 999 : BigDecimal.valueOf(stock).divide(daily, 0, RoundingMode.FLOOR).intValue();
                LocalDate stockout = coverage < FORECAST_DAYS && daily.signum() > 0 ? today.plusDays(Math.max(0, coverage)) : null;
                String risk = stock <= product.reorderPoint || stock < forecast ? "HIGH" : coverage <= product.supplierLeadTimeDays ? "MEDIUM" : "LOW";
                List<String> evidence = new ArrayList<>();
                evidence.add(units90 + " units sold in the 90-day history window");
                evidence.add("Weighted demand: 40% 90-day average + 60% recent 30-day average");
                if (scenario == AgentScenario.COMPONENT_SUPPLY_CONSTRAINT && safe.sku() != null && !safe.sku().isBlank()
                        && safe.sku().trim().equalsIgnoreCase(product.sku)) {
                    evidence.add("Component supply constraint applies to this selected SKU: supplier replenishment is treated as unavailable until the planner validates an alternative.");
                }
                if (scenario == AgentScenario.ROUTE_SHIPPING_DELAY) evidence.add("Route-shipping delay: transfer arrival timing is extended before feasibility is assessed.");
                if (signalApplies && signalAdjustment.signum() != 0) {
                    evidence.add("Operator-supplied signal adjustment for this selected SKU: "
                            + signalAdjustment.stripTrailingZeros().toPlainString() + "%");
                }
                DemandForecast forecastView = new DemandForecast(product.sku, product.name, warehouse, stock,
                        product.reorderPoint, units90, avg90.setScale(2, RoundingMode.HALF_UP), trend.setScale(2, RoundingMode.HALF_UP),
                        forecast, coverage, stockout, risk, evidence);
                forecasts.add(forecastView);
                byKey.put(key(product.sku, warehouse), forecastView);
            }
        }
        forecasts.sort(Comparator.comparingInt((DemandForecast forecast) -> riskRank(forecast.risk()))
                .reversed().thenComparing(DemandForecast::sku).thenComparing(DemandForecast::warehouse));
        List<TransferRecommendation> recommendations = recommendTransfers(catalog, byKey, scenario, safe.warehouse());
        List<DemandForecast> visibleForecasts = forecasts.stream()
                .filter(forecast -> safe.warehouse() == null || forecast.warehouse() == safe.warehouse())
                .toList();
        BusinessImpact impact = impact(catalog, visibleForecasts, recommendations);
        String scope = safe.sku() == null || safe.sku().isBlank() ? "All SKUs" : safe.sku().trim();
        if (safe.warehouse() != null) scope += " · " + safe.warehouse().displayName;
        List<String> assumptions = new ArrayList<>(List.of(
                "Read-only agent workflow: it inspects products and the last 90 days of sales, then compares feasible inter-warehouse routes.",
                "Forecast uses a weighted 90-day and recent 30-day demand rate; it is a decision aid, not a promise of future sales.",
                "Route costs are planning estimates: Abu Dhabi–Dubai AED 55, Abu Dhabi–Sharjah AED 40, Dubai–Sharjah AED 35 per transfer.",
                "Recommendations reserve the source warehouse's reorder point and never execute a transfer automatically."));
        if (scenario == AgentScenario.COMPONENT_SUPPLY_CONSTRAINT) assumptions.add("Only the selected SKU is treated as component-constrained; unrelated SKUs are not assumed to be affected.");
        if (scenario == AgentScenario.ROUTE_SHIPPING_DELAY) assumptions.add("Route delay adds three days and 25% to the estimated transfer cost; verify the current carrier notice before approval.");
        if (signals.isBlank()) assumptions.add("No external signal was supplied; the forecast uses recorded demand only.");
        else if (signalHasSkuScope) assumptions.add("External signal supplied by the operator and applied only to the selected SKU: " + signals);
        else assumptions.add("External signal supplied by the operator was not applied: select the affected SKU to avoid changing forecasts for unrelated products.");
        if (scenario == AgentScenario.COMPONENT_SUPPLY_CONSTRAINT && !signalHasSkuScope) {
            assumptions.add("A component constraint requires a selected SKU; no product was marked as affected.");
        }
        String narrative = fallbackNarrative(forecasts, recommendations, impact);
        String mode = "RULE_BASED_AGENT";
        if (Boolean.FALSE.equals(safe.useAi())) {
            mode = "RULE_BASED_AGENT";
        } else {
            String prompt = prompt(scope, scenario, signals, forecasts, recommendations, impact);
            try {
                Optional<String> generated = ai.generate(prompt);
                if (generated.isPresent()) {
                    narrative = generated.get();
                    mode = "OLLAMA_LOCAL_AGENT";
                } else if (ai.configured()) {
                    assumptions.add("The local Ollama model returned no narrative; the deterministic advisor result is shown.");
                    mode = "RULE_BASED_FALLBACK";
                } else {
                    assumptions.add("Ollama is unavailable. Start the local Ollama server; deterministic analysis remains available.");
                }
            } catch (ResponseStatusException exception) {
                if (!exception.getStatusCode().is5xxServerError()) throw exception;
                assumptions.add("The local Ollama model failed for this request; the deterministic advisor result is shown instead.");
                mode = "RULE_BASED_FALLBACK";
            }
        }
        List<String> nextActions = recommendations.isEmpty()
                ? List.of("Review low-risk supplier replenishment and keep monitoring daily sales.")
                : List.of("Validate the suggested route and stock reservation with the branch owner.",
                        "Approve any physical move separately in Replenishments; this analysis never changes inventory.",
                        "Re-run after adding campaign, seasonality, or supplier-delay signals.");
        return new AgentAnalysis(now, mode, HISTORY_DAYS, scope, scenario, signals, visibleForecasts, recommendations, impact, assumptions, nextActions, narrative);
    }

    public Map<String, Object> status() {
        return Map.of("configured", ai.configured(), "provider", "Ollama local", "model", ai.model(),
                "modeWhenUnavailable", "RULE_BASED_AGENT", "historyDays", HISTORY_DAYS, "writesInventory", false,
                "enablement", "Start Ollama locally and install the configured model with `ollama pull " + ai.model() + "`.");
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        String message = request.message().trim();
        Map<String, Object> context = request.context() == null ? Map.of() : request.context();
        String contextJson = serializeContext(context);
        String conversation = (request.history() == null ? List.<AgentChatTurn>of() : request.history()).stream()
                .map(turn -> turn.role() + ": " + turn.content())
                .collect(Collectors.joining("\n"));
        String prompt = "Answer the current user message directly and naturally. Use prior conversation only to resolve "
                + "references such as 'that SKU' or 'what about Dubai'; the latest user message is the task. "
                + "For business facts, use the latest application data below as the source of truth. Select only the "
                + "records relevant to the question; do not dump or summarize unrelated sections. Explain important "
                + "numbers in plain language, include units and AED where relevant, and use short paragraphs or Markdown "
                + "bullets when they improve readability. If the data does not support an answer, say what is missing "
                + "instead of guessing. Treat forecasts as estimates, transfer/replenishment suggestions as proposals, "
                + "and existing records according to their status; never imply an order, sale, or transfer was executed "
                + "unless the supplied records explicitly confirm it.\n"
                + "\nConversation history (context only):\n" + (conversation.isBlank() ? "(none)" : conversation)
                + "\n\nCurrent user message:\n" + message
                + "\n\nLatest application data (JSON; may include unrelated records):\n" + contextJson;
        String answer = fallbackChat(message, context);
        String mode = "RULE_BASED_CHAT";
        if (!Boolean.FALSE.equals(request.useAi())) {
            Optional<String> generated = ai.generate(prompt);
            if (generated.isPresent()) {
                answer = generated.get();
                mode = "OLLAMA_LOCAL_CHAT";
            } else if (ai.configured()) {
                mode = "RULE_BASED_CHAT_FALLBACK";
            }
        }
        List<String> sources = context.keySet().stream().map(key -> switch (key) {
            case "overview" -> "GET /api/v1/overview";
            case "products" -> "GET /api/v1/products";
            case "sales" -> "GET /api/v1/sales";
            case "replenishments" -> "GET /api/v1/replenishments";
            case "plans" -> "GET /api/v1/plans";
            case "context" -> "GET /api/v1/context";
            case "advisor" -> "POST /api/v1/agent/analyze (read-only)";
            default -> key;
        }).toList();
        return new AgentChatResponse(Instant.now(), mode, answer, sources, planningProposal(message, context));
    }

    private PlanRequest planningProposal(String message, Map<String, Object> context) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (!(normalized.contains("plan") || normalized.contains("scenario") || normalized.contains("update")
                || normalized.contains("supply constraint") || normalized.contains("shipping delay"))) return null;
        Matcher matcher = SKU.matcher(message.toUpperCase(Locale.ROOT));
        String sku = matcher.find() ? matcher.group() : firstSku(context);
        if (sku == null || sku.isBlank()) return null;
        Warehouse warehouse = normalized.contains("dubai") ? Warehouse.DUBAI
                : normalized.contains("sharjah") ? Warehouse.SHARJAH : Warehouse.ABU_DHABI;
        DisruptionType type = normalized.contains("shipping") || normalized.contains("route") || normalized.contains("transport")
                ? DisruptionType.TRANSPORT_DELAY
                : normalized.contains("demand") || normalized.contains("surge") ? DisruptionType.DEMAND_SURGE : DisruptionType.SUPPLIER_DELAY;
        int leadTime = type == DisruptionType.TRANSPORT_DELAY ? 3 : type == DisruptionType.SUPPLIER_DELAY ? 7 : 0;
        String title = type == DisruptionType.TRANSPORT_DELAY ? "Route-shipping delay · " + sku : "Component supply constraint · " + sku;
        String note = type == DisruptionType.TRANSPORT_DELAY
                ? "Review a three-day route-shipping delay for " + sku + " in " + warehouse.displayName + "; compare branch transfer timing and cost before approval."
                : "Review a component supply constraint for " + sku + " in " + warehouse.displayName + "; treat supplier replenishment as delayed and validate a branch transfer or alternative source.";
        return new PlanRequest(title, sku, warehouse, type, BigDecimal.ZERO, leadTime, 0, null, note, PlanStatus.DRAFT);
    }

    private static String firstSku(Map<String, Object> context) {
        Object productsValue = context.get("products");
        if (!(productsValue instanceof Map<?, ?> products)) return null;
        Object contentValue = products.get("content");
        if (!(contentValue instanceof List<?> content)) return null;
        for (Object value : content) {
            if (value instanceof Map<?, ?> product && product.get("sku") != null) return product.get("sku").toString();
        }
        return null;
    }

    private String fallbackChat(String message, Map<String, Object> context) {
        String normalized = message.toLowerCase(Locale.ROOT);
        Object overviewValue = context.get("overview");
        if (overviewValue instanceof Map<?, ?> overview) {
            String base = "Live data shows " + value(overview, "skuCount") + " SKUs and " + value(overview, "totalStock")
                    + " units across the network, with " + value(overview, "unitsSold30Days") + " units sold in the last 30 days.";
            if (normalized.contains("revenue") || normalized.contains("sales")) {
                return base + " Recorded 30-day revenue is AED " + value(overview, "revenue30Days") + ".";
            }
            if (normalized.contains("stock") || normalized.contains("inventory") || normalized.contains("low")) {
                return base + " Review the returned product and low-stock records for the affected branch before moving stock.";
            }
            return base + " I used the live overview endpoint; ask about a SKU, branch, sales, transfers, or scenarios for a narrower answer.";
        }
        return "I fetched the requested live REST data, but there is not enough structured context to answer confidently. Try asking about stock, sales, transfers, or planning scenarios.";
    }

    private String serializeContext(Map<String, Object> context) {
        try {
            String value = json.writeValueAsString(withoutSensitiveLedgerFields(context));
            if (value.length() > 30000) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "The data context is too large for chat. Ask about a narrower SKU, branch, or date range.");
            }
            return value;
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The supplied data context could not be processed.");
        }
    }

    private static Object withoutSensitiveLedgerFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String field = Objects.toString(key, "");
                if (!field.equalsIgnoreCase("customer") && !field.equalsIgnoreCase("reference")) {
                    sanitized.put(field, withoutSensitiveLedgerFields(nestedValue));
                }
            });
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(AgentService::withoutSensitiveLedgerFields).toList();
        }
        return value;
    }

    private static Object value(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "—" : value;
    }

    private List<TransferRecommendation> recommendTransfers(List<Product> catalog, Map<String, DemandForecast> forecasts,
                                                             AgentScenario scenario, Warehouse destinationScope) {
        List<TransferRecommendation> result = new ArrayList<>();
        for (Product product : catalog) {
            Map<Warehouse, Long> remainingBySource = new EnumMap<>(Warehouse.class);
            for (Warehouse source : Warehouse.values()) {
                DemandForecast sourceForecast = forecasts.get(key(product.sku, source));
                if (sourceForecast != null) {
                    remainingBySource.put(source, Math.max(0, sourceForecast.currentStock()
                            - sourceForecast.reorderPoint() - sourceForecast.forecast30Days()));
                }
            }
            for (Warehouse destination : Warehouse.values()) {
                if (destinationScope != null && destinationScope != destination) continue;
                DemandForecast destinationForecast = forecasts.get(key(product.sku, destination));
                if (destinationForecast == null) continue;
                long shortage = shortage(destinationForecast);
                if (shortage <= 0) continue;
                List<Candidate> candidates = Arrays.stream(Warehouse.values()).filter(source -> source != destination)
                        .filter(remainingBySource::containsKey)
                        .map(source -> new Candidate(source, forecasts.get(key(product.sku, source)), remainingBySource.get(source)))
                        .filter(candidate -> candidate.available() > 0)
                        .sorted(Comparator.comparing((Candidate candidate) -> routeCost(candidate.source(), destination, scenario))
                                .thenComparingInt(candidate -> routeDays(candidate.source(), destination, scenario)))
                        .toList();
                long remaining = shortage;
                for (Candidate candidate : candidates) {
                    if (remaining <= 0) break;
                    int days = routeDays(candidate.source(), destination, scenario);
                    LocalDate stockoutDate = destinationForecast.projectedStockoutDate();
                    if (stockoutDate != null && LocalDate.now(UAE).plusDays(days).isAfter(stockoutDate)) continue;
                    int quantity = (int) Math.min(remaining, candidate.available());
                    if (quantity <= 0) continue;
                    long after = Math.max(0, shortage - quantity);
                    BigDecimal cost = routeCost(candidate.source(), destination, scenario);
                    String tradeoff = "Protects " + quantity + " units of destination availability for " + money(cost)
                            + "; consumes source safety stock above its reorder point.";
                    String rationale = "Source has " + candidate.available() + " units available after its forecast and reorder reserve.";
                    if (scenario == AgentScenario.ROUTE_SHIPPING_DELAY) rationale += " Route arrival is delayed by three days; confirm it arrives before the destination stockout.";
                    result.add(new TransferRecommendation(product.sku, product.name, candidate.source(), destination, quantity,
                            days, cost, shortage, after, tradeoff, rationale));
                    remainingBySource.put(candidate.source(), candidate.available() - quantity);
                    remaining -= quantity;
                }
            }
        }
        return result;
    }

    private BusinessImpact impact(List<Product> catalog, List<DemandForecast> forecasts, List<TransferRecommendation> recommendations) {
        long before = forecasts.stream().mapToLong(AgentService::shortage).sum();
        long after = before - recommendations.stream().mapToLong(recommendation -> recommendation.quantity()).sum();
        BigDecimal revenueBefore = BigDecimal.ZERO;
        for (Product product : catalog) {
            long productShortage = forecasts.stream().filter(f -> f.sku().equals(product.sku)).mapToLong(AgentService::shortage).sum();
            revenueBefore = revenueBefore.add(product.unitPrice.multiply(BigDecimal.valueOf(productShortage)));
        }
        BigDecimal transferCost = recommendations.stream().map(TransferRecommendation::routeCostAed).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal revenueAfter = revenueBefore;
        for (Product product : catalog) {
            long moved = recommendations.stream().filter(r -> r.sku().equals(product.sku)).mapToLong(TransferRecommendation::quantity).sum();
            revenueAfter = revenueAfter.subtract(product.unitPrice.multiply(BigDecimal.valueOf(Math.min(moved, forecasts.stream().filter(f -> f.sku().equals(product.sku)).mapToLong(AgentService::shortage).sum()))));
        }
        long avoided = Math.max(0, before - Math.max(0, after));
        return new BusinessImpact(before, Math.max(0, after), money(revenueBefore), money(revenueAfter), avoided,
                money(transferCost), money(revenueBefore.subtract(revenueAfter).subtract(transferCost)));
    }

    private String fallbackNarrative(List<DemandForecast> forecasts, List<TransferRecommendation> recommendations, BusinessImpact impact) {
        long high = forecasts.stream().filter(f -> "HIGH".equals(f.risk())).count();
        if (recommendations.isEmpty()) return "The 90-day demand review found " + high + " high-risk warehouse positions and no transfer with enough source surplus to recommend. Protect service levels with supplier replenishment or add an external signal and re-run.";
        return "The agent found " + high + " high-risk positions and recommends " + recommendations.stream().mapToInt(TransferRecommendation::quantity).sum()
                + " units across " + recommendations.size() + " feasible route(s). The proposed moves reduce modeled shortage from "
                + impact.shortageUnitsBefore() + " to " + impact.shortageUnitsAfter() + " units, with estimated route cost " + money(impact.transferCostAed()) + ".";
    }

    private String prompt(String scope, AgentScenario scenario, String signals, List<DemandForecast> forecasts, List<TransferRecommendation> recommendations, BusinessImpact impact) {
        String facts = forecasts.stream().limit(60).map(f -> f.sku() + " " + f.warehouse() + " stock=" + f.currentStock()
                + " reorderPoint=" + f.reorderPoint() + " forecast30=" + f.forecast30Days() + " risk=" + f.risk())
                .collect(Collectors.joining("; "));
        String routes = recommendations.stream().map(r -> r.sku() + " " + r.fromWarehouse() + "->" + r.toWarehouse() + " qty=" + r.quantity() + " cost=" + r.routeCostAed()).collect(Collectors.joining("; "));
        return "Explain this inventory network review for " + scope + " in plain language. "
                + "Active scenario: " + scenario + ". Operator-provided external signal: " + (signals.isBlank() ? "none" : signals) + ". "
                + "Use only the supplied facts. If the signal is none, do not imply a demand increase or other outside event. "
                + "In 2-4 short sentences, state the main modeled risk and what the proposed transfers could change; mention "
                + "the transfer cost and revenue-at-risk figures only if useful. Use AED for every currency amount. "
                + "These are model estimates, not observed outcomes. 'Before' and 'after' compare a baseline with what could "
                + "happen if proposed transfers were implemented; they do not mean stock has moved or risk has already fallen. "
                + "Do not tell the user to execute, approve, or implement a transfer. Describe transfers as options for human review. "
                + "Do not call a proposal optimized, successful, or completed. Do not infer that low-risk positions need no action. "
                + "If facts are missing or unclear, say so briefly. "
                + "\nForecast facts (stock and demand are units): " + facts
                + "\nFeasible transfer proposals (not executed): " + routes
                + "\nModeled impact: shortage units before=" + impact.shortageUnitsBefore()
                + ", after proposed transfers=" + impact.shortageUnitsAfter()
                + ", revenue at risk before AED=" + impact.revenueAtRiskBefore()
                + ", after proposed transfers AED=" + impact.revenueAtRiskAfter()
                + ", proposed route cost AED=" + impact.transferCostAed() + ".";
    }

    private static Map<String, Map<Warehouse, Long>> quantities(List<Sale> rows, Instant from, Instant to) {
        return rows.stream().filter(sale -> !sale.soldAt.isBefore(from) && sale.soldAt.isBefore(to)).collect(Collectors.groupingBy(
                sale -> sale.product.sku, Collectors.groupingBy(sale -> sale.warehouse, Collectors.summingLong(sale -> sale.quantity))));
    }

    private static long get(Map<String, Map<Warehouse, Long>> values, String sku, Warehouse warehouse) {
        return values.getOrDefault(sku, Map.of()).getOrDefault(warehouse, 0L);
    }

    private static BigDecimal perDay(long value, int days) {
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal signalAdjustment(String signals) {
        if (signals == null || signals.isBlank()) return BigDecimal.ZERO;
        Matcher matcher = PERCENT.matcher(signals);
        if (matcher.find()) return new BigDecimal(matcher.group(1)).max(new BigDecimal("-50")).min(new BigDecimal("100"));
        String normalized = signals.toLowerCase(Locale.ROOT);
        if (normalized.contains("surge") || normalized.contains("increase") || normalized.contains("campaign")) return new BigDecimal("10");
        if (normalized.contains("drop") || normalized.contains("decrease") || normalized.contains("slow")) return new BigDecimal("-10");
        return BigDecimal.ZERO;
    }

    private static long shortage(DemandForecast forecast) {
        return Math.max(0, forecast.forecast30Days() + forecast.reorderPoint() - forecast.currentStock());
    }

    private static String key(String sku, Warehouse warehouse) { return sku + "|" + warehouse; }
    private static String route(Warehouse from, Warehouse to) { return from.name() + "->" + to.name(); }
    private static int riskRank(String risk) {
        return switch (risk) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }
    private static BigDecimal routeCost(Warehouse from, Warehouse to, AgentScenario scenario) {
        BigDecimal base = ROUTE_COST.get(route(from, to));
        return scenario == AgentScenario.ROUTE_SHIPPING_DELAY ? base.multiply(new BigDecimal("1.25")) : base;
    }
    private static int routeDays(Warehouse from, Warehouse to, AgentScenario scenario) {
        int base = ROUTE_DAYS.get(route(from, to));
        return scenario == AgentScenario.ROUTE_SHIPPING_DELAY ? base + 3 : base;
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private record Candidate(Warehouse source, DemandForecast forecast, long available) {
        private Candidate(Warehouse source, DemandForecast forecast) { this(source, forecast, 0); }
    }
}
