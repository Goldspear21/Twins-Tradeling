(() => {
  const views = {
    overview: {
      title: "Overview",
      heading: "Stock overview",
      eyebrow: "A CLEAR VIEW OF YOUR BUSINESS",
      description: "The right stock. In the right city. All in one place.",
    },
    inventory: {
      title: "Product inventory",
      heading: "Product inventory",
      eyebrow: "YOUR PRODUCT CATALOG",
      description: "Browse products and their stock across all branches.",
    },
    sales: {
      title: "Sales ledger",
      heading: "Sales ledger",
      eyebrow: "RECORDED SALES",
      description: "Review recorded sales or add a completed sale.",
    },
    transfers: {
      title: "Replenishments",
      heading: "Replenishments",
      eyebrow: "COMPLETED STOCK MOVEMENTS",
      description: "Review completed transfers between your branches.",
    },
    planning: {
      title: "30-day planning",
      heading: "30-day planning",
      eyebrow: "PLAN AHEAD",
      description: "Explore what-if scenarios without changing inventory.",
    },
    api: {
      title: "API & model context",
      heading: "API & model context",
      eyebrow: "ONE CONTRACT. ANY CLIENT.",
      description: "Explore the read-only API and business context.",
    },
  };

  const viewSections = [...document.querySelectorAll(".view")];
  const viewButtons = [...document.querySelectorAll("[data-view]")];
  const toast = document.querySelector("#toast");
  let toastTimer;

  function showToast(message) {
    if (!toast) return;
    toast.textContent = message;
    toast.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => {
      toast.hidden = true;
    }, 3500);
  }

  function showView(name, updateHash = true) {
    const view = views[name] ? name : "overview";
    viewSections.forEach((section) => {
      section.hidden = section.id !== `view-${view}`;
    });
    viewButtons.forEach((button) => {
      const active = button.dataset.view === view;
      button.classList.toggle("active", active);
      if (active) {
        button.setAttribute("aria-current", "page");
      } else {
        button.removeAttribute("aria-current");
      }
    });

    const page = views[view];
    document.querySelector("#breadcrumb-title").textContent = page.title;
    document.querySelector("#page-title").textContent = page.heading;
    document.querySelector("#page-eyebrow").textContent = page.eyebrow;
    document.querySelector("#page-description").textContent = page.description;
    if (updateHash && window.location.hash !== `#${view}`) {
      window.history.replaceState(null, "", `#${view}`);
    }
  }

  function closeDialog(dialog) {
    if (dialog?.open) dialog.close();
  }

  document.addEventListener("click", (event) => {
    const viewButton = event.target.closest("[data-view]");
    if (viewButton) {
      event.preventDefault();
      showView(viewButton.dataset.view);
      return;
    }

    const openButton = event.target.closest("[data-open]");
    if (openButton) {
      const dialog = document.getElementById(openButton.dataset.open);
      if (dialog instanceof HTMLDialogElement) dialog.showModal();
      return;
    }

    const closeButton = event.target.closest("[data-close]");
    if (closeButton) {
      closeDialog(closeButton.closest("dialog"));
      return;
    }

    if (event.target.closest("#view-low-stock")) {
      showView("inventory");
      const lowStockFilter = document.querySelector("#low-stock-filter");
      if (lowStockFilter) lowStockFilter.checked = true;
      return;
    }

    if (event.target.closest("#refresh-button")) {
      const updated = document.querySelector("#last-updated");
      if (updated) updated.textContent = `Workspace opened · ${new Date().toLocaleTimeString()}`;
      showToast("Workspace refreshed.");
    }
  });

  document.querySelectorAll("dialog").forEach((dialog) => {
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) closeDialog(dialog);
    });
  });

  const consoleButton = document.querySelector("#run-console");
  consoleButton?.addEventListener("click", async () => {
    const path = document.querySelector("#console-path")?.value;
    const output = document.querySelector("#console-output");
    if (!path || !output) return;
    output.textContent = "Loading…";
    try {
      const response = await fetch(path, {
        headers: { Accept: "application/json" },
      });
      const body = await response.json();
      output.textContent = JSON.stringify(body, null, 2);
    } catch (error) {
      output.textContent = `Could not load the API response: ${error.message}`;
    }
  });

  window.addEventListener("hashchange", () => {
    showView(window.location.hash.slice(1), false);
  });

  const initialView = window.location.hash.slice(1);
  showView(initialView, false);
})();
