package com.example.crm.frontend.backend.deactivation;

import java.util.List;
import java.util.Optional;

/** Port na produkty zákazníka (v legacy viacero backend služieb). */
public interface CustomerProductService {

    /** Produkty zákazníka. */
    List<CustomerProduct> productsOf(String customerId);

    /** Produkt len vtedy, ak patrí zákazníkovi – podvrhnuté ID produktu iného zákazníka vráti prázdny výsledok. */
    Optional<CustomerProduct> find(String customerId, String productId);
}
