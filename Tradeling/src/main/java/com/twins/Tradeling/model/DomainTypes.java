package com.twins.Tradeling.model;

public final class DomainTypes {
    private DomainTypes() {}

    public enum Warehouse {
        ABU_DHABI("Abu Dhabi"), DUBAI("Dubai"), SHARJAH("Sharjah");

        public final String displayName;

        Warehouse(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum ProductFamily { MOUSE, KEYBOARD, CHARGER, CABLE, HUB }
    public enum DisruptionType { SUPPLIER_DELAY, DEMAND_SURGE, TRANSPORT_DELAY, DEMAND_DROP }
    public enum AgentScenario { NORMAL, COMPONENT_SUPPLY_CONSTRAINT, ROUTE_SHIPPING_DELAY }
    public enum PlanStatus { DRAFT, CONFIRMED, CLOSED }
}
