package com.example.crm.frontend.backend.deactivation;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Vymyslené produkty demo zákazníkov (ID zákazníkov zodpovedajú SSO mocku). */
public class InMemoryCustomerProductService implements CustomerProductService {

    private final List<CustomerProduct> products = Arrays.asList(
            new CustomerProduct("T-1", "C-1001", "Tarif Neobmedzený", ProductType.TARIFF, "Neobmedzené dáta, volania a SMS"),
            new CustomerProduct("T-2", "C-1001", "Tarif Štandard", ProductType.TARIFF, "5 GB dát, 100 minút"),
            new CustomerProduct("P-1", "C-1001", "+421 900 000 001", ProductType.PRODUCT, "Mobilné číslo – Tarif Neobmedzený"),
            new CustomerProduct("T-3", "C-1002", "Firemný tarif", ProductType.TARIFF, "Zdieľané dáta pre firmu"),
            new CustomerProduct("P-2", "C-1002", "+421 900 000 002", ProductType.PRODUCT, "Mobilné číslo – Firemný tarif"));

    @Override
    public List<CustomerProduct> productsOf(String customerId) {
        return products.stream().filter(p -> p.getCustomerId().equals(customerId)).collect(Collectors.toList());
    }

    @Override
    public Optional<CustomerProduct> find(String customerId, String productId) {
        return productsOf(customerId).stream().filter(p -> p.getId().equals(productId)).findFirst();
    }
}
