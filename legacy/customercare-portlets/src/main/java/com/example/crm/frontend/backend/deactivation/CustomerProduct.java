package com.example.crm.frontend.backend.deactivation;

/** Produkt alebo tarif zákazníka (zjednodušené – v legacy bohatý model produktového katalógu). */
public final class CustomerProduct {

    private final String id;
    private final String customerId;
    private final String name;
    private final ProductType type;
    private final String description;

    public CustomerProduct(String id, String customerId, String name, ProductType type, String description) {
        this.id = id;
        this.customerId = customerId;
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public ProductType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
