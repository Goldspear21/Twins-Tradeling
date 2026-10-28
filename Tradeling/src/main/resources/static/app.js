(() => {
  const warehouses = ["ABU_DHABI", "DUBAI", "SHARJAH"];
  const warehouseNames = {
    ABU_DHABI: "Abu Dhabi",
    DUBAI: "Dubai",
    SHARJAH: "Sharjah",
  };
  const views = {
    overview: ["Overview", "Stock overview", "A CLEAR VIEW OF YOUR BUSINESS", "The right stock. In the right city. All in one place."],
    inventory: ["Product inventory", "Product inventory", "YOUR PRODUCT CATALOG", "Browse products and their stock across all branches."],
    sales: ["Sales ledger", "Sales ledger", "RECORDED SALES", "Review recorded sales or add a completed sale."],
    transfers: ["Replenishments", "Replenishments", "COMPLETED STOCK MOVEMENTS", "Review completed transfers between your branches."],
    planning: ["30-day planning", "30-day planning", "PLAN AHEAD", "Explore what-if scenarios without changing inventory."],
    advisor: ["AI operations advisor", "AI operations advisor", "FORECAST · ROUTE · IMPACT", "Use live inventory and 90 days of sales history to prepare the next move."],
    api: ["API & model context", "API & model context", "ONE CONTRACT. ANY CLIENT.", "Explore the read-only API and business context."],
  };
  const pageState = { inventory: 0, sales: 0, transfers: 0, planning: 0 };
  const pageSize = 200;
  const products = new Map();
  const chatHistory = [];
  const toast = document.querySelector("#toast");
  let toastTimer;
  let searchTimer;

  const $ = (selector, root = document) => root.querySelector(selector);
  const make = (tag, className, text) => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
  };

  function showToast(message) {
    toast.textContent = message;
    toast.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => { toast.hidden = true; }, 4000);
  }

  function showError(message) {
    const box = $("#global-error");
    box.textContent = message;
    box.hidden = !message;
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}), ...options.headers },
    });
    const text = await response.text();
    let body;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      throw new Error(`The server returned an invalid response (${response.status}).`);
    }
    if (!response.ok) {
      const fieldErrors = body?.fieldErrors ? Object.entries(body.fieldErrors).map(([field, error]) => `${field}: ${error}`).join("; ") : "";
      throw new Error([body?.message || `Request failed (${response.status}).`, fieldErrors].filter(Boolean).join(" "));
    }
    return body;
  }

  function showView(name, updateHash = true) {
    const view = views[name] ? name : "overview";
    document.querySelectorAll(".view").forEach((section) => { section.hidden = section.id !== `view-${view}`; });
    document.querySelectorAll("[data-view]").forEach((button) => {
      const active = button.dataset.view === view;
      button.classList.toggle("active", active);
      if (active) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    });
    const [title, heading, eyebrow, description] = views[view];
    $("#breadcrumb-title").textContent = title;
    $("#page-title").textContent = heading;
    $("#page-eyebrow").textContent = eyebrow;
    $("#page-description").textContent = description;
    if (updateHash && window.location.hash !== `#${view}`) window.history.replaceState(null, "", `#${view}`);
    loadView(view).catch((error) => showError(error.message));
  }

  function closeDialog(dialog) {
    if (dialog?.open) dialog.close();
  }

  function money(value) {
    return new Intl.NumberFormat("en-AE", { style: "currency", currency: "AED", maximumFractionDigits: 2 }).format(Number(value || 0));
  }

  function number(value) {
    return new Intl.NumberFormat("en-AE").format(Number(value || 0));
  }

  function dateTime(value) {
    return value ? new Intl.DateTimeFormat("en-AE", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Dubai" }).format(new Date(value)) : "—";
  }

  function setEmptyRow(body, columns, message) {
    body.replaceChildren();
    const row = make("tr");
    const cell = make("td", "empty-cell", message);
    cell.colSpan = columns;
    row.append(cell);
    body.append(row);
  }

  function addPagination(element, page, totalPages, totalElements, onPage) {
    element.replaceChildren();
    const label = make("span", "", totalElements === 0 ? "No records" : `Page ${page + 1} of ${Math.max(totalPages, 1)} · ${number(totalElements)} records`);
    const actions = make("div", "pagination-actions");
    const previous = make("button", "", "Previous");
    previous.type = "button";
    previous.disabled = page <= 0;
    previous.addEventListener("click", () => onPage(page - 1));
    const next = make("button", "", "Next");
    next.type = "button";
    next.disabled = page + 1 >= totalPages;
    next.addEventListener("click", () => onPage(page + 1));
    actions.append(previous, next);
    element.append(label, actions);
  }

  function queryString(values) {
    const query = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => {
      if (value !== "" && value !== null && value !== undefined && value !== false) query.set(key, String(value));
    });
    return query.toString();
  }

  async function loadProducts() {
    const params = queryString({
      q: $("#product-search").value.trim(),
      family: $("#family-filter").value,
      warehouse: $("#warehouse-filter").value,
      lowStock: $("#low-stock-filter").checked,
      page: pageState.inventory,
      size: pageSize,
    });
    const result = await api(`/api/v1/products?${params}`);
    result.content.forEach((product) => products.set(product.sku, product));
    $("#inventory-count").textContent = `${number(result.totalElements)} products`;
    const body = $("#inventory-body");
    body.replaceChildren();
    if (!result.content.length) setEmptyRow(body, 7, "No products match these filters.");
    result.content.forEach((product) => {
      const row = make("tr");
      const productCell = make("td");
      productCell.append(make("strong", "", product.name), make("small", "table-subtitle", product.sku));
      row.append(productCell, make("td", "", product.family), make("td", "", money(product.unitPrice)));
      warehouses.forEach((warehouse) => {
        const stock = product.inventory.find((item) => item.warehouse === warehouse);
        const cell = make("td");
        cell.append(make("span", stock?.lowStock ? "pill low" : "pill neutral", `${number(stock?.quantity)} units`));
        row.append(cell);
      });
      row.append(make("td", "", number(product.totalStock)));
      body.append(row);
    });
    addPagination($("#inventory-pagination"), result.page, result.totalPages, result.totalElements, (page) => {
      pageState.inventory = page;
      loadProducts().catch((error) => showError(error.message));
    });
    updateProductSelects();
  }

  async function loadProductCatalog() {
    const catalog = new Map();
    let page = 0;
    let result;
    do {
      result = await api(`/api/v1/products?${queryString({ page, size: pageSize })}`);
      result.content.forEach((product) => catalog.set(product.sku, product));
      page++;
    } while (page < result.totalPages);
    products.clear();
    catalog.forEach((product, sku) => products.set(sku, product));
    updateProductSelects();
    $("#nav-sku-count").textContent = number(result.totalElements);
  }

  function updateProductSelects() {
    const allProducts = [...products.values()].sort((a, b) => a.sku.localeCompare(b.sku));
    document.querySelectorAll("select[data-product-select]").forEach((select) => {
      const previous = select.value;
      const firstText = select.options[0]?.textContent || "All products";
      const firstValue = select.options[0]?.value || "";
      select.replaceChildren(new Option(firstText, firstValue));
      allProducts.forEach((product) => select.add(new Option(`${product.name} · ${product.sku}`, product.sku)));
      if (allProducts.some((product) => product.sku === previous)) select.value = previous;
    });
  }

  function dateAtUaeStart(date, addDay = false) {
    if (!date) return "";
    const value = new Date(`${date}T00:00:00+04:00`);
    if (addDay) value.setUTCDate(value.getUTCDate() + 1);
    return value.toISOString();
  }

  async function loadSales() {
    const from = $("#sale-from").value;
    const to = $("#sale-to").value;
    const result = await api(`/api/v1/sales?${queryString({
      sku: $("#sale-sku-filter").value,
      warehouse: $("#warehouse-filter").value,
      from: dateAtUaeStart(from),
      to: dateAtUaeStart(to, true),
      page: pageState.sales,
      size: pageSize,
    })}`);
    const body = $("#sales-body");
    body.replaceChildren();
    if (!result.content.length) setEmptyRow(body, 7, "No sales found for these filters.");
    result.content.forEach((sale) => {
      const row = make("tr");
      const date = make("td");
      date.append(make("strong", "", sale.reference), make("small", "table-subtitle", dateTime(sale.soldAt)));
      row.append(date);
      const productCell = make("td");
      productCell.append(make("strong", "", sale.productName), make("small", "table-subtitle", sale.sku));
      row.append(productCell, make("td", "", sale.customer), make("td", "", warehouseNames[sale.warehouse] || sale.warehouse),
        make("td", "", number(sale.quantity)), make("td", "", money(sale.unitPrice)), make("td", "", money(sale.totalPrice)));
      body.append(row);
    });
    addPagination($("#sales-pagination"), result.page, result.totalPages, result.totalElements, (page) => {
      pageState.sales = page;
      loadSales().catch((error) => showError(error.message));
    });
  }

  async function loadTransfers() {
    const result = await api(`/api/v1/replenishments?${queryString({
      sku: $("#transfer-sku-filter").value,
      warehouse: $("#warehouse-filter").value,
      page: pageState.transfers,
      size: pageSize,
    })}`);
    const body = $("#transfers-body");
    body.replaceChildren();
    if (!result.content.length) setEmptyRow(body, 6, "No completed transfers found for these filters.");
    result.content.forEach((transfer) => {
      const row = make("tr");
      const date = make("td");
      date.append(make("strong", "", transfer.reference), make("small", "table-subtitle", dateTime(transfer.movedAt)));
      const productCell = make("td");
      productCell.append(make("strong", "", transfer.productName), make("small", "table-subtitle", transfer.sku));
      row.append(date, productCell, make("td", "", warehouseNames[transfer.fromWarehouse] || transfer.fromWarehouse),
        make("td", "", warehouseNames[transfer.toWarehouse] || transfer.toWarehouse), make("td", "", number(transfer.quantity)),
        make("td", "", transfer.reason));
      body.append(row);
    });
    addPagination($("#transfers-pagination"), result.page, result.totalPages, result.totalElements, (page) => {
      pageState.transfers = page;
      loadTransfers().catch((error) => showError(error.message));
    });
  }

  function renderPlan(plan) {
    const card = make("article", "plan-card");
    const header = make("div", "plan-card-head");
    header.append(make("span", "", `${plan.disruptionType.replaceAll("_", " ")} · ${warehouseNames[plan.warehouse]}`),
      make("span", `pill ${plan.firstShortageDate ? "low" : "mint"}`, plan.status));
    const title = make("h3", "", plan.title);
    const subtitle = make("p", "plan-subtitle", `${plan.productName} · ${plan.sku} · ${plan.demandChangePercent}% demand change`);
    const numbers = make("div", "plan-numbers");
    const demand = make("div");
    demand.append(make("span", "", "Expected demand"), make("strong", "", `${number(plan.expectedDemand30Days)} units`));
    const ending = make("div");
    ending.append(make("span", "", "Projected ending stock"), make("strong", "", `${Number(plan.projectedEndingStock).toFixed(1)} units`));
    numbers.append(demand, ending);
    const risk = make("p", `plan-risk ${plan.firstShortageDate ? "risk" : ""}`,
      plan.firstShortageDate ? `Possible shortage: ${plan.firstShortageDate}` : "No projected stockout in this 30-day scenario.");
    const note = make("p", "plan-action-note", plan.actionNote);
    const actions = make("div", "plan-card-actions");
    const projection = make("button", "text-button", "View projection");
    projection.type = "button";
    projection.addEventListener("click", () => showProjection(plan.id));
    actions.append(projection);
    card.append(header, title, subtitle, numbers, risk, note, actions);
    return card;
  }

  async function loadPlanning() {
    const result = await api(`/api/v1/plans?${queryString({
      sku: $("#plan-form [name=sku]").value,
      warehouse: $("#warehouse-filter").value,
      status: $("#plan-status-filter").value,
      page: pageState.planning,
      size: pageSize,
    })}`);
    const cards = $("#plan-cards");
    cards.replaceChildren();
    if (!result.content.length) cards.append(make("div", "empty-state", "No scenarios found. Create a what-if scenario to get started."));
    result.content.forEach((plan) => cards.append(renderPlan(plan)));
    addPagination($("#planning-pagination"), result.page, result.totalPages, result.totalElements, (page) => {
      pageState.planning = page;
      loadPlanning().catch((error) => showError(error.message));
    });
    if (result.content[0]) {
      $("#horizon-label").textContent = `Rolling horizon: ${result.content[0].horizonStart} to ${result.content[0].horizonEnd} · recalculated from live data.`;
    }
  }

  async function loadAdvisorStatus() {
    try {
      const status = await api("/api/v1/agent/status");
      $("#advisor-mode").textContent = status.configured ? `Local Ollama · ${status.model}` : "Ollama not configured · rule-based";
      $("#advisor-mode").className = `pill ${status.configured ? "mint" : "neutral"}`;
    } catch (error) {
      $("#advisor-mode").textContent = "Advisor unavailable";
      $("#advisor-mode").className = "pill low";
    }
  }

  function renderAdvisor(analysis) {
    const result = $("#advisor-result");
    result.replaceChildren();
    const impact = analysis.businessImpact;
    const metrics = make("div", "metrics advisor-metrics");
    [["Scenario", analysis.scenario.replaceAll("_", " "), `${analysis.historyDays}-day history`],
      ["Shortage before proposals", `${number(impact.shortageUnitsBefore)} units`, "modeled risk"],
      ["Shortage after proposals", `${number(impact.shortageUnitsAfter)} units`, `${number(impact.avoidedShortageUnits)} fewer in the model`],
      ["Estimated net value protected", money(impact.netValueProtectedAed), `after ${money(impact.transferCostAed)} proposed route cost`]].forEach(([label, value, note]) => {
      const card = make("div", "metric");
      card.append(make("span", "metric-note", label), make("div", "metric-value", value), make("p", "metric-note", note));
      metrics.append(card);
    });
    const transferCount = analysis.transferRecommendations.length;
    const transferSummary = transferCount
      ? `If all ${transferCount} proposed transfers were completed, the model estimates that could fall to ${number(impact.shortageUnitsAfter)} units (${number(impact.avoidedShortageUnits)} fewer), with an estimated route cost of ${money(impact.transferCostAed)}. These are planning estimates; no transfers have been made.`
      : `No feasible transfers were identified. The model estimates ${number(impact.shortageUnitsAfter)} shortage units remain; review replenishment options. These are planning estimates, not completed actions.`;
    const takeaway = make("div", "advisor-takeaway");
    takeaway.append(
      make("strong", "", "In plain language"),
      make("p", "", `The model estimates ${number(impact.shortageUnitsBefore)} shortage units before proposed transfers. ${transferSummary}`)
    );
    const narrative = make("div", "panel advisor-narrative");
    narrative.append(make("div", "panel-heading", undefined));
    const narrativeHeading = $(".panel-heading", narrative);
    narrativeHeading.append(make("div", "", "AI explanation"), make("span", `pill ${analysis.mode === "OLLAMA_LOCAL_AGENT" ? "mint" : "neutral"}`, analysis.mode.replaceAll("_", " ")));
    const narrativeCopy = make("div", "advisor-copy");
    renderChatMarkdown(narrativeCopy, analysis.narrative);
    narrative.append(narrativeCopy);
    const impactLine = make("p", "projection-meta", `Estimated revenue at risk: ${money(impact.revenueAtRiskBefore)} before proposals and ${money(impact.revenueAtRiskAfter)} after proposals · ${analysis.transferRecommendations.length} proposed routes, not executed.`);
    narrative.append(impactLine);
    const layout = make("div", "advisor-grid");
    const forecastPanel = make("div", "panel");
    forecastPanel.append(make("div", "panel-heading", undefined));
    const forecastHeading = $(".panel-heading", forecastPanel);
    forecastHeading.append(make("h2", "", "Demand forecast and risk"), make("span", "pill mint", `${analysis.demandForecasts.length} positions`));
    const forecastScroll = make("div", "table-scroll");
    const forecastTable = make("table");
    const forecastHead = make("thead");
    const forecastRow = make("tr");
    ["SKU / branch", "90-day sales", "30-day forecast", "Coverage", "Risk"].forEach((label) => forecastRow.append(make("th", "", label)));
    forecastHead.append(forecastRow);
    const forecastBody = make("tbody");
    analysis.demandForecasts.slice(0, 40).forEach((forecast) => {
      const row = make("tr");
      const sku = make("td");
      sku.append(make("strong", "", forecast.sku), make("small", "table-subtitle", `${forecast.productName} · ${warehouseNames[forecast.warehouse] || forecast.warehouse}`));
      row.append(sku, make("td", "", number(forecast.unitsSold90Days)), make("td", "", `${number(forecast.forecast30Days)} units`),
        make("td", "", forecast.coverageDays >= 999 ? "—" : `${number(forecast.coverageDays)} days`), make("td", `pill ${forecast.risk === "HIGH" ? "low" : forecast.risk === "MEDIUM" ? "neutral" : "mint"}`, forecast.risk));
      forecastBody.append(row);
    });
    if (!analysis.demandForecasts.length) setEmptyRow(forecastBody, 5, "No matching products or sales history.");
    forecastTable.append(forecastHead, forecastBody);
    forecastScroll.append(forecastTable);
    forecastPanel.append(forecastScroll);
    const transferPanel = make("div", "panel");
    transferPanel.append(make("div", "panel-heading", undefined));
    const transferHeading = $(".panel-heading", transferPanel);
    transferHeading.append(make("h2", "", "Feasible transfer trade-offs"), make("span", "pill mint", `${analysis.transferRecommendations.length} routes`));
    const transferBody = make("div", "advisor-transfer-list");
    if (!analysis.transferRecommendations.length) transferBody.append(make("div", "empty-state", "No source warehouse has enough surplus above its reserve to recommend a move."));
    analysis.transferRecommendations.forEach((recommendation) => {
      const item = make("article", "advisor-transfer");
      const title = make("div", "advisor-transfer-title");
      title.append(make("strong", "", `${recommendation.sku} · ${warehouseNames[recommendation.fromWarehouse]} → ${warehouseNames[recommendation.toWarehouse]}`), make("span", "pill mint", `${number(recommendation.quantity)} units`));
      item.append(title, make("p", "", `${money(recommendation.routeCostAed)} route cost · ${recommendation.routeDays} day · ${recommendation.tradeoff}`), make("small", "table-subtitle", recommendation.rationale));
      transferBody.append(item);
    });
    transferPanel.append(transferBody);
    layout.append(forecastPanel, transferPanel);
    const details = make("div", "advisor-details");
    const assumptions = make("div", "panel advisor-list");
    assumptions.append(make("div", "panel-heading", undefined));
    $(".panel-heading", assumptions).append(make("h2", "", "Assumptions and next actions"));
    const list = make("ul", "warning-list");
    [...analysis.assumptions, ...analysis.nextActions.map((action) => `Next: ${action}`)].forEach((item) => list.append(make("li", "", item)));
    assumptions.append(list);
    details.append(assumptions);
    result.append(metrics, takeaway, narrative, layout, details);
  }

  async function runAdvisorAnalysis() {
    const button = $("#run-advisor");
    const error = $("#advisor-error");
    error.hidden = true;
    button.disabled = true;
    button.textContent = "Analyzing…";
    try {
      const analysis = await api("/api/v1/agent/analyze", { method: "POST", body: JSON.stringify({
        sku: $("#advisor-sku").value.trim() || null,
        warehouse: $("#advisor-warehouse").value || null,
        scenario: $("#advisor-scenario").value,
        externalSignals: $("#advisor-signals").value.trim(),
        useAi: true,
      }) });
      renderAdvisor(analysis);
    } catch (errorValue) {
      error.textContent = errorValue.message;
      error.hidden = false;
    } finally {
      button.disabled = false;
      button.innerHTML = '<svg class="icon"><use href="#icon-spark"/></svg>Run network analysis';
    }
  }

  async function loadOverview() {
    const overview = await api("/api/v1/overview");
    const metrics = $("#metrics");
    metrics.replaceChildren();
    [
      ["Products", number(overview.skuCount), "SKUs across all branches"],
      ["Available stock", number(overview.totalStock), "Units across your network"],
      ["Sales · 30 days", number(overview.unitsSold30Days), `${money(overview.revenue30Days)} recorded revenue`],
      ["Active scenarios", number(overview.activeScenarios), "Saved planning proposals"],
    ].forEach(([label, value, note]) => {
      const card = make("div", "metric");
      card.append(make("span", "metric-note", label), make("div", "metric-value", value), make("p", "metric-note", note));
      metrics.append(card);
    });
    const branchCards = $("#warehouse-cards");
    branchCards.replaceChildren();
    overview.warehouses.forEach((branch) => {
      const card = make("article", "warehouse-card");
      card.append(make("div", "warehouse-head"), make("h3", "warehouse-title", branch.city),
        make("div", "warehouse-stock", `${number(branch.stockUnits)} units`),
        make("p", "metric-note", `${number(branch.unitsSold30Days)} sold · ${money(branch.revenue30Days)} revenue`),
        make("p", "metric-note", `${number(branch.lowStockSkus)} low-stock SKUs`));
      branchCards.append(card);
    });
    const body = $("#attention-body");
    body.replaceChildren();
    const lowStock = overview.lowStockProducts.flatMap((product) => product.inventory
      .filter((stock) => stock.lowStock && (!$("#warehouse-filter").value || stock.warehouse === $("#warehouse-filter").value))
      .map((stock) => ({ product, stock }))).slice(0, 8);
    if (!lowStock.length) setEmptyRow(body, 4, "No low-stock products to show.");
    lowStock.forEach(({ product, stock }) => {
      const row = make("tr");
      const cell = make("td");
      cell.append(make("strong", "", product.name), make("small", "table-subtitle", product.sku));
      row.append(cell, make("td", "", stock.city), make("td", "", `${stock.quantity} / ${stock.reorderPoint} reorder point`),
        make("td", "", stock.quantity === 0 ? "Out of stock" : "Low stock"));
      body.append(row);
    });
    $("#data-mode").textContent = "Live database";
    $("#last-updated").textContent = `Last updated · ${dateTime(overview.asOf)}`;
    $("#nav-sku-count").textContent = number(overview.skuCount);
  }

  async function showProjection(id) {
    try {
      const plan = await api(`/api/v1/plans/${id}`);
      $("#projection-title").textContent = plan.title;
      const content = $("#projection-content");
      content.replaceChildren();
      const summary = make("div", "projection-summary");
      [
        ["SKU / branch", `${plan.sku} · ${warehouseNames[plan.warehouse]}`],
        ["Starting stock", `${number(plan.startingStock)} units`],
        ["Projected demand", `${number(plan.expectedDemand30Days)} units`],
      ].forEach(([label, value]) => {
        const item = make("div");
        item.append(make("span", "", label), make("strong", "", value));
        summary.append(item);
      });
      const details = make("p", "projection-meta",
        `Baseline ${plan.baselineStart}–${plan.baselineEnd}: ${number(plan.baselineUnitsSold)} sold · adjusted daily demand ${Number(plan.adjustedDailyDemand).toFixed(2)} units · replenishment recommendation ${number(plan.recommendedReplenishmentQuantity)} units.`);
      const table = make("div", "projection-table");
      const tableElement = make("table");
      const head = make("thead");
      const headRow = make("tr");
      ["Date", "Expected demand", "Inbound", "Projected stock"].forEach((label) => headRow.append(make("th", "", label)));
      head.append(headRow);
      const body = make("tbody");
      plan.dailyProjection.forEach((day) => {
        const row = make("tr");
        [day.date, Number(day.expectedDemand).toFixed(2), number(day.plannedInbound), Number(day.projectedNetStock).toFixed(2)]
          .forEach((value) => row.append(make("td", "", value)));
        body.append(row);
      });
      tableElement.append(head, body);
      table.append(tableElement);
      content.append(summary, details, table);
      $("#projection-dialog").showModal();
    } catch (error) {
      showToast(error.message);
    }
  }

  function appendChatMessage(role, text, source) {
    const message = make("div", `chat-message ${role}`);
    if (source) message.append(make("span", "chat-mode-label", source));
    const body = make("div", "chat-message-body");
    if (role === "assistant") renderChatMarkdown(body, text);
    else body.textContent = text;
    message.append(body);
    $("#chat-messages").append(message);
    $("#chat-messages").scrollTop = $("#chat-messages").scrollHeight;
    return message;
  }

  function renderChatMarkdown(container, markdown) {
    const lines = String(markdown ?? "").replaceAll("\r\n", "\n").split("\n");
    let index = 0;
    while (index < lines.length) {
      const line = lines[index];
      if (!line.trim()) {
        index += 1;
        continue;
      }

      if (/^\s*```/.test(line)) {
        const code = [];
        index += 1;
        while (index < lines.length && !/^\s*```/.test(lines[index])) code.push(lines[index++]);
        if (index < lines.length) index += 1;
        const pre = make("pre", "chat-code-block");
        const codeElement = make("code");
        codeElement.textContent = code.join("\n");
        pre.append(codeElement);
        container.append(pre);
        continue;
      }

      if (index + 1 < lines.length && line.includes("|") && isMarkdownTableRule(lines[index + 1])) {
        const headers = splitMarkdownTableRow(line);
        const rows = [];
        index += 2;
        while (index < lines.length && lines[index].trim() && lines[index].includes("|")) {
          rows.push(splitMarkdownTableRow(lines[index++]));
        }
        container.append(buildMarkdownTable(headers, rows));
        continue;
      }

      const heading = line.match(/^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/);
      if (heading) {
        const element = make(`h${Math.min(heading[1].length + 2, 6)}`, "chat-markdown-heading");
        appendInlineMarkdown(element, heading[2]);
        container.append(element);
        index += 1;
        continue;
      }

      const listItem = line.match(/^\s*([-*+]|\d+[.)])\s+(.+)$/);
      if (listItem) {
        const ordered = /^\d/.test(listItem[1]);
        const list = make(ordered ? "ol" : "ul", "chat-markdown-list");
        while (index < lines.length) {
          const item = lines[index].match(/^\s*([-*+]|\d+[.)])\s+(.+)$/);
          if (!item || /^\d/.test(item[1]) !== ordered) break;
          const li = make("li");
          appendInlineMarkdown(li, item[2]);
          list.append(li);
          index += 1;
        }
        container.append(list);
        continue;
      }

      if (/^\s*>\s?/.test(line)) {
        const quote = make("blockquote", "chat-markdown-quote");
        while (index < lines.length && /^\s*>\s?/.test(lines[index])) {
          const paragraph = make("p");
          appendInlineMarkdown(paragraph, lines[index++].replace(/^\s*>\s?/, ""));
          quote.append(paragraph);
        }
        container.append(quote);
        continue;
      }

      if (/^\s*(---+|___+|\*\*\*+)\s*$/.test(line)) {
        container.append(document.createElement("hr"));
        index += 1;
        continue;
      }

      const paragraphLines = [];
      while (index < lines.length && lines[index].trim() && !startsMarkdownBlock(lines, index)) {
        paragraphLines.push(lines[index++].trim());
      }
      if (!paragraphLines.length) {
        paragraphLines.push(lines[index++].trim());
      }
      const paragraph = make("p", "chat-markdown-paragraph");
      appendInlineMarkdown(paragraph, paragraphLines.join(" "));
      container.append(paragraph);
    }
  }

  function startsMarkdownBlock(lines, index) {
    const line = lines[index];
    return /^\s{0,3}(#{1,6})\s+/.test(line)
      || /^\s*([-*+]|\d+[.)])\s+/.test(line)
      || /^\s*>\s?/.test(line)
      || /^\s*```/.test(line)
      || /^\s*(---+|___+|\*\*\*+)\s*$/.test(line)
      || (line.includes("|") && index + 1 < lines.length && isMarkdownTableRule(lines[index + 1]));
  }

  function isMarkdownTableRule(line) {
    const cells = splitMarkdownTableRow(line);
    return cells.length > 1 && cells.every((cell) => /^:?-{3,}:?$/.test(cell.trim()));
  }

  function splitMarkdownTableRow(line) {
    let row = line.trim();
    if (row.startsWith("|")) row = row.slice(1);
    if (row.endsWith("|")) row = row.slice(0, -1);
    return row.split(/(?<!\\)\|/).map((cell) => cell.replaceAll("\\|", "|").trim());
  }

  function buildMarkdownTable(headers, rows) {
    const wrapper = make("div", "chat-table-wrap");
    const table = make("table", "chat-markdown-table");
    const thead = document.createElement("thead");
    const headerRow = document.createElement("tr");
    headers.forEach((value) => {
      const th = document.createElement("th");
      appendInlineMarkdown(th, value);
      headerRow.append(th);
    });
    thead.append(headerRow);
    const tbody = document.createElement("tbody");
    rows.forEach((row) => {
      const tr = document.createElement("tr");
      headers.forEach((_, cellIndex) => {
        const td = document.createElement("td");
        appendInlineMarkdown(td, row[cellIndex] || "");
        tr.append(td);
      });
      tbody.append(tr);
    });
    table.append(thead, tbody);
    wrapper.append(table);
    return wrapper;
  }

  function appendInlineMarkdown(parent, text) {
    const tokenPattern = /(\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)|\*\*([^*]+)\*\*|__([^_]+)__|~~([^~]+)~~|`([^`]+)`|\*([^*\n]+)\*|_([^_\n]+)_)/g;
    let position = 0;
    for (const match of text.matchAll(tokenPattern)) {
      appendEmojiAwareText(parent, text.slice(position, match.index));
      let element;
      if (match[2] && match[3]) {
        element = make("a", "chat-markdown-link", match[2]);
        element.href = match[3];
        element.target = "_blank";
        element.rel = "noopener noreferrer";
      } else if (match[4] || match[5]) {
        element = make("strong", "", match[4] || match[5]);
      } else if (match[6]) {
        element = make("del", "", match[6]);
      } else if (match[7]) {
        element = make("code", "chat-inline-code", match[7]);
      } else {
        element = make("em", "", match[8] || match[9]);
      }
      parent.append(element);
      position = match.index + match[0].length;
    }
    appendEmojiAwareText(parent, text.slice(position));
  }

  function appendEmojiAwareText(parent, text) {
    if (!text) return;
    if (typeof Intl.Segmenter !== "function") {
      parent.append(document.createTextNode(text));
      return;
    }
    const segmenter = new Intl.Segmenter(undefined, { granularity: "grapheme" });
    for (const { segment } of segmenter.segment(text)) {
      if (/\p{Extended_Pictographic}/u.test(segment)) {
        parent.append(make("span", "chat-emoji", segment));
      } else {
        parent.append(document.createTextNode(segment));
      }
    }
  }

  async function reviewPlanningProposal(proposal) {
    showView("planning");
    await loadProductCatalog();
    const form = $("#plan-form");
    form.querySelector("[name=title]").value = proposal.title;
    form.querySelector("[name=sku]").value = proposal.sku;
    form.querySelector("[name=warehouse]").value = proposal.warehouse;
    form.querySelector("[name=disruptionType]").value = proposal.disruptionType;
    form.querySelector("[name=demandChangePercent]").value = proposal.demandChangePercent;
    form.querySelector("[name=additionalLeadTimeDays]").value = proposal.additionalLeadTimeDays;
    form.querySelector("[name=plannedInboundQuantity]").value = proposal.plannedInboundQuantity;
    form.querySelector("[name=inboundDate]").value = proposal.inboundDate || "";
    form.querySelector("[name=actionNote]").value = proposal.actionNote;
    form.querySelector("[name=status]").value = proposal.status;
    $("#plan-dialog-title").textContent = "Review AI planning proposal";
    $("#plan-dialog").showModal();
  }

  function appendPlanningProposal(proposal) {
    if (!proposal) return;
    const card = make("div", "chat-plan-proposal");
    card.append(make("strong", "", `Planning proposal · ${proposal.title}`), make("small", "", `${proposal.sku} · ${warehouseNames[proposal.warehouse] || proposal.warehouse} · ${proposal.disruptionType.replaceAll("_", " ")}`));
    const button = make("button", "button secondary", "Review in 30-day planning");
    button.type = "button";
    button.addEventListener("click", () => reviewPlanningProposal(proposal).catch((error) => showError(error.message)));
    card.append(button);
    $("#chat-messages").append(card);
    $("#chat-messages").scrollTop = $("#chat-messages").scrollHeight;
  }

  async function fetchChatData(question) {
    const lower = question.toLowerCase();
    const branch = lower.includes("abu dhabi") ? "ABU_DHABI"
      : lower.includes("dubai") ? "DUBAI"
        : lower.includes("sharjah") ? "SHARJAH" : null;
    const sku = question.match(/\b[A-Z]{2,6}-[A-Z0-9-]{3,30}\b/i)?.[0]?.toUpperCase() || null;
    const needsProducts = /product|sku|stock|inventory|low stock|branch|warehouse|available|restock/.test(lower);
    const needsSales = /sale|sold|revenue|demand|history/.test(lower);
    const needsTransfers = /transfer|move|route|replenish|source|destination/.test(lower);
    const needsPlans = /plan|scenario|supplier|inbound/.test(lower);
    const needsAdvisor = /forecast|risk|recommend|shortage|restock|transfer option|move stock/.test(lower);
    const requests = [];
    if (needsProducts) {
      const params = new URLSearchParams({ size: sku ? "1" : "50" });
      if (sku) params.set("q", sku);
      if (branch) params.set("warehouse", branch);
      if (/low stock|restock|shortage|risk/.test(lower)) params.set("lowStock", "true");
      requests.push(["products", `/api/v1/products?${params}`]);
    }
    if (needsSales) requests.push(["sales", `/api/v1/sales?size=${sku ? 10 : 20}${sku ? `&sku=${encodeURIComponent(sku)}` : ""}${branch ? `&warehouse=${branch}` : ""}`]);
    if (needsTransfers) requests.push(["replenishments", `/api/v1/replenishments?size=20${sku ? `&sku=${encodeURIComponent(sku)}` : ""}`]);
    if (needsPlans) requests.push(["plans", "/api/v1/plans?size=5"]);
    if (needsAdvisor) requests.push(["advisor", "/api/v1/agent/analyze"]);
    if (!requests.length || /overview|network|total/.test(lower)) requests.push(["overview", "/api/v1/overview"]);

    const entries = await Promise.all(requests.map(async ([name, path]) => {
      if (name !== "advisor") return [name, await api(path)];
      const analysis = await api(path, { method: "POST", body: JSON.stringify({
        sku,
        warehouse: branch,
        scenario: "NORMAL",
        useAi: false,
      }) });
      return [name, compactAdvisor(analysis, branch)];
    }));
    const context = Object.fromEntries(entries.map(([name, value]) => [name, compactChatData(name, value)]));
    return boundChatContext(context);
  }

  function boundChatContext(context) {
    const budget = 24000;
    const dropped = {};
    const trimOrder = [
      ["sales", "content"],
      ["replenishments", "content"],
      ["plans", "content"],
      ["advisor", "demandForecasts"],
      ["advisor", "transferRecommendations"],
      ["products", "content"],
    ];
    for (const [section, listName] of trimOrder) {
      const items = context[section]?.[listName];
      while (Array.isArray(items) && items.length > 1 && JSON.stringify(context).length > budget) {
        items.pop();
        dropped[section] = (dropped[section] || 0) + 1;
      }
    }
    if (JSON.stringify(context).length > budget && context.advisor) {
      context.advisor.assumptions = context.advisor.assumptions.slice(0, 3);
      context.advisor.narrative = context.advisor.narrative.slice(0, 1000);
    }
    if (Object.keys(dropped).length) context.dataLimited = dropped;
    return context;
  }

  function compactAdvisor(analysis, branch) {
    const forecasts = branch
      ? analysis.demandForecasts.filter((item) => item.warehouse === branch)
      : analysis.demandForecasts;
    return {
      scope: analysis.scope,
      scenario: analysis.scenario,
      demandForecasts: forecasts.slice(0, 15).map((item) => ({
        sku: item.sku,
        productName: item.productName,
        warehouse: item.warehouse,
        currentStock: item.currentStock,
        reorderPoint: item.reorderPoint,
        unitsSold90Days: item.unitsSold90Days,
        forecast30Days: item.forecast30Days,
        coverageDays: item.coverageDays,
        projectedStockoutDate: item.projectedStockoutDate,
        risk: item.risk,
      })),
      transferRecommendations: analysis.transferRecommendations.slice(0, 10),
      businessImpact: analysis.businessImpact,
      narrative: analysis.narrative,
      assumptions: analysis.assumptions.slice(0, 6),
    };
  }

  function compactChatData(name, data) {
    if (name === "overview") {
      return {
        asOf: data.asOf,
        currency: data.currency,
        skuCount: data.skuCount,
        totalStock: data.totalStock,
        unitsSold30Days: data.unitsSold30Days,
        revenue30Days: data.revenue30Days,
        activeScenarios: data.activeScenarios,
        warehouses: data.warehouses,
      };
    }
    if (name === "products") {
      return {
        totalElements: data.totalElements,
        content: data.content.map((item) => ({
          sku: item.sku,
          name: item.name,
          family: item.family,
          totalStock: item.totalStock,
          reorderPoint: item.reorderPoint,
          inventory: item.inventory,
        })),
      };
    }
    if (name === "sales") {
      return { content: data.content.map(({ sku: itemSku, productName, warehouse, quantity, unitPrice, totalPrice, soldAt }) =>
        ({ sku: itemSku, productName, warehouse, quantity, unitPrice, totalPrice, soldAt })) };
    }
    if (name === "replenishments") {
      return { content: data.content };
    }
    if (name === "plans") {
      return { content: data.content.map((item) => ({
        title: item.title,
        sku: item.sku,
        warehouse: item.warehouse,
        disruptionType: item.disruptionType,
        status: item.status,
        demandChangePercent: item.demandChangePercent,
        additionalLeadTimeDays: item.additionalLeadTimeDays,
        recommendedReplenishmentQuantity: item.recommendedReplenishmentQuantity,
        firstShortageDate: item.firstShortageDate,
        warnings: item.warnings,
      })) };
    }
    return data;
  }

  async function submitChat(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const input = $("#chat-input");
    const button = $("button[type=submit]", form);
    const question = input.value.trim();
    if (!question) return;
    appendChatMessage("user", question);
    input.value = "";
    input.disabled = true;
    button.disabled = true;
    button.textContent = "…";
    try {
      const context = await fetchChatData(question);
      const response = await api("/api/v1/agent/chat", {
        method: "POST",
        body: JSON.stringify({ message: question, context, history: chatHistory.slice(-4), useAi: true }),
      });
      const source = response.mode === "OLLAMA_LOCAL_CHAT"
        ? "Local Ollama"
        : "Rule-based fallback · local model not used";
      appendChatMessage("assistant", response.answer, source);
      chatHistory.push({ role: "user", content: question }, { role: "assistant", content: response.answer });
      if (chatHistory.length > 4) chatHistory.splice(0, chatHistory.length - 4);
      appendPlanningProposal(response.planningProposal);
      const sources = $("#chat-sources");
      sources.replaceChildren();
      response.sources.forEach((source) => sources.append(make("span", "pill neutral", source)));
      $("#chat-sources-wrap").hidden = response.sources.length === 0;
    } catch (error) {
      appendChatMessage("assistant", `I could not complete that request: ${error.message}`);
    } finally {
      input.disabled = false;
      button.disabled = false;
      button.textContent = "Ask";
      input.focus();
    }
  }

  async function loadView(view) {
    showError("");
    await loadProductCatalog();
    if (view === "overview") await loadOverview();
    if (view === "inventory") await loadProducts();
    if (view === "sales") await loadSales();
    if (view === "transfers") await loadTransfers();
    if (view === "planning") await loadPlanning();
    if (view === "advisor") await loadAdvisorStatus();
  }

  function formValues(form) {
    return Object.fromEntries(new FormData(form).entries());
  }

  function reference(prefix) {
    return `${prefix}-${Date.now()}`;
  }

  function setFormError(form, message) {
    const error = $(".form-error", form);
    error.textContent = message;
    error.hidden = !message;
  }

  async function submitForm(form, path, method, payload, successMessage) {
    const button = $('button[type="submit"]', form);
    setFormError(form, "");
    button.disabled = true;
    try {
      await api(path, { method, body: JSON.stringify(payload) });
      form.reset();
      closeDialog(form.closest("dialog"));
      showToast(successMessage);
      await refreshAll().catch((error) => showError(`Your changes were saved, but the display could not refresh: ${error.message}`));
    } catch (error) {
      setFormError(form, error.message);
    } finally {
      button.disabled = false;
    }
  }

  async function refreshAll() {
    await loadProductCatalog();
    await Promise.all([loadOverview(), loadProducts(), loadSales(), loadTransfers(), loadPlanning()]);
  }

  function dateFilterChanged() {
    pageState.sales = 0;
    loadSales().catch((error) => showError(error.message));
  }

  document.addEventListener("click", async (event) => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    const nav = target.closest("[data-view]");
    if (nav) {
      event.preventDefault();
      showView(nav.dataset.view);
      return;
    }
    const open = target.closest("[data-open]");
    if (open) {
      const dialog = document.getElementById(open.dataset.open);
      if (dialog instanceof HTMLDialogElement) {
        if (dialog.querySelector("select[data-product-select]")) {
          try {
            await loadProductCatalog();
          } catch (error) {
            showError(`Could not load products from the database: ${error.message}`);
            return;
          }
        }
        if (open.dataset.open === "sale-dialog") $("#sale-form [name=reference]").value = reference("SALE");
        if (open.dataset.open === "transfer-dialog") $("#transfer-form [name=reference]").value = reference("MOVE");
        dialog.showModal();
      }
      return;
    }
    const close = target.closest("[data-close]");
    if (close) close.closest("dialog")?.close();
    if (target.closest("#view-low-stock")) {
      showView("inventory");
      $("#low-stock-filter").checked = true;
      pageState.inventory = 0;
      loadProducts().catch((error) => showError(error.message));
    }
    if (target.closest("#refresh-button")) {
      refreshAll().then(() => showToast("Workspace refreshed.")).catch((error) => showError(error.message));
    }
  });

  document.querySelectorAll("dialog").forEach((dialog) => {
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) dialog.close();
    });
  });

  $("#product-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const values = formValues(event.currentTarget);
    submitForm(event.currentTarget, "/api/v1/products", "POST", {
      sku: values.sku.trim().toUpperCase(),
      details: {
        name: values.name.trim(), family: values.family, description: values.description.trim(), brand: values.brand.trim(),
        unitPrice: Number(values.unitPrice), costPrice: Number(values.costPrice), reorderPoint: Number(values.reorderPoint),
        supplierLeadTimeDays: Number(values.supplierLeadTimeDays),
      },
      inventory: { abuDhabi: Number(values.abuDhabi), dubai: Number(values.dubai), sharjah: Number(values.sharjah) },
    }, "Product saved with opening stock.");
  });

  $("#sale-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const values = formValues(event.currentTarget);
    submitForm(event.currentTarget, "/api/v1/sales", "POST", {
      sku: values.sku, reference: values.reference.trim(), customer: values.customer.trim(),
      warehouse: values.warehouse, quantity: Number(values.quantity),
      ...(values.unitPrice ? { unitPrice: Number(values.unitPrice) } : {}),
    }, "Sale recorded and stock updated.");
  });

  $("#transfer-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const values = formValues(event.currentTarget);
    submitForm(event.currentTarget, "/api/v1/replenishments", "POST", {
      sku: values.sku, reference: values.reference.trim(), fromWarehouse: values.fromWarehouse,
      toWarehouse: values.toWarehouse, quantity: Number(values.quantity), reason: values.reason.trim(),
    }, "Transfer recorded and branch stock updated.");
  });

  function planPayload(form) {
    const values = formValues(form);
    return {
      title: values.title.trim(), sku: values.sku, warehouse: values.warehouse,
      disruptionType: values.disruptionType, demandChangePercent: Number(values.demandChangePercent),
      additionalLeadTimeDays: Number(values.additionalLeadTimeDays), plannedInboundQuantity: Number(values.plannedInboundQuantity),
      inboundDate: values.inboundDate || null, actionNote: values.actionNote.trim(), status: values.status,
    };
  }

  $("#plan-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = formValues(form);
    const id = values.id;
    submitForm(form, id ? `/api/v1/plans/${encodeURIComponent(id)}` : "/api/v1/plans", id ? "PUT" : "POST",
      planPayload(form), "Planning scenario saved.");
  });

  $("#preview-plan").addEventListener("click", async () => {
    const form = $("#plan-form");
    const preview = $("#plan-preview");
    setFormError(form, "");
    if (!form.reportValidity()) return;
    try {
      const plan = await api("/api/v1/plans/preview", { method: "POST", body: JSON.stringify(planPayload(form)) });
      preview.replaceChildren();
      preview.hidden = false;
      const summary = make("div", "projection-summary");
      [
        ["Baseline sales", `${number(plan.baselineUnitsSold)} units / 30 days`],
        ["Projected demand", `${number(plan.expectedDemand30Days)} units`],
        ["Replenishment recommendation", `${number(plan.recommendedReplenishmentQuantity)} units`],
      ].forEach(([label, value]) => {
        const item = make("div");
        item.append(make("span", "", label), make("strong", "", value));
        summary.append(item);
      });
      const risk = make("p", `plan-risk ${plan.firstShortageDate ? "risk" : ""}`,
        plan.firstShortageDate ? `Possible shortage on ${plan.firstShortageDate}.` : "No projected stockout in this scenario.");
      const detailButton = make("button", "button secondary", "View daily projection");
      detailButton.type = "button";
      detailButton.addEventListener("click", () => {
        $("#projection-title").textContent = "Scenario preview";
        const content = $("#projection-content");
        content.replaceChildren();
        const table = make("div", "projection-table");
        const tableElement = make("table");
        const head = make("thead");
        const headRow = make("tr");
        ["Date", "Demand", "Inbound", "Projected stock"].forEach((label) => headRow.append(make("th", "", label)));
        head.append(headRow);
        const body = make("tbody");
        plan.dailyProjection.forEach((day) => {
          const row = make("tr");
          [day.date, Number(day.expectedDemand).toFixed(2), number(day.plannedInbound), Number(day.projectedNetStock).toFixed(2)]
            .forEach((value) => row.append(make("td", "", value)));
          body.append(row);
        });
        tableElement.append(head, body);
        table.append(tableElement);
        content.append(table);
        $("#projection-dialog").showModal();
      });
      preview.append(summary, risk, detailButton);
    } catch (error) {
      setFormError(form, error.message);
    }
  });

  $("#product-search").addEventListener("input", () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(() => {
      pageState.inventory = 0;
      loadProducts().catch((error) => showError(error.message));
    }, 250);
  });
  $("#family-filter").addEventListener("change", () => { pageState.inventory = 0; loadProducts().catch((error) => showError(error.message)); });
  $("#low-stock-filter").addEventListener("change", () => { pageState.inventory = 0; loadProducts().catch((error) => showError(error.message)); });
  $("#sale-sku-filter").addEventListener("change", dateFilterChanged);
  $("#sale-from").addEventListener("change", dateFilterChanged);
  $("#sale-to").addEventListener("change", dateFilterChanged);
  $("#transfer-sku-filter").addEventListener("change", () => { pageState.transfers = 0; loadTransfers().catch((error) => showError(error.message)); });
  $("#plan-status-filter").addEventListener("change", () => { pageState.planning = 0; loadPlanning().catch((error) => showError(error.message)); });
  $("#warehouse-filter").addEventListener("change", () => {
    Object.keys(pageState).forEach((key) => { pageState[key] = 0; });
    refreshAll().catch((error) => showError(error.message));
  });
  $("#run-advisor").addEventListener("click", runAdvisorAnalysis);
  const chatDrawer = $("#chat-drawer");
  const chatOpenButton = $("#chat-open");
  chatOpenButton.addEventListener("click", () => {
    chatDrawer.showModal();
    $("#chat-input").focus();
  });
  $("#chat-close").addEventListener("click", () => chatDrawer.close());
  chatDrawer.addEventListener("click", (event) => {
    if (event.target === chatDrawer) chatDrawer.close();
  });
  $("#chat-form").addEventListener("submit", submitChat);
  $("#chat-reset").addEventListener("click", () => {
    chatHistory.length = 0;
    $("#chat-messages").replaceChildren();
    appendChatMessage("assistant", "Ask a question or choose a suggestion. I’ll fetch relevant live data and explain the evidence.");
    $("#chat-sources").replaceChildren();
    $("#chat-sources-wrap").hidden = true;
  });
  document.querySelectorAll("[data-chat-prompt]").forEach((button) => button.addEventListener("click", () => {
    $("#chat-input").value = button.dataset.chatPrompt;
    $("#chat-form").requestSubmit();
  }));
  $("#run-console").addEventListener("click", async () => {
    const output = $("#console-output");
    output.textContent = "Loading…";
    try {
      const result = await api($("#console-path").value);
      output.textContent = JSON.stringify(result, null, 2);
    } catch (error) {
      output.textContent = `Could not load the API response: ${error.message}`;
    }
  });

  window.addEventListener("hashchange", () => showView(window.location.hash.slice(1), false));
  showView(window.location.hash.slice(1) || "overview", false);
})();
