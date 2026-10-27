package com.twins.Tradeling.service;

import java.time.Instant;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class QuerySupport {
    private QuerySupport() {}

    public static Pageable page(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0 and size must be between 1 and 200");
        }
        return PageRequest.of(page, size, sort);
    }

    public static <T> Specification<T> ledger(String sku, Object warehouse, String customer,
                                               Instant from, Instant to, String dateField, boolean transfer) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be earlier than to (exclusive)");
        }
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (sku != null && !sku.isBlank()) {
                predicates.add(builder.equal(root.get("product").get("sku"), sku.trim()));
            }
            if (warehouse != null) {
                predicates.add(transfer
                        ? builder.or(builder.equal(root.get("fromWarehouse"), warehouse), builder.equal(root.get("toWarehouse"), warehouse))
                        : builder.equal(root.get("warehouse"), warehouse));
            }
            if (customer != null && !customer.isBlank()) {
                predicates.add(builder.equal(root.get("customer"), customer.trim()));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get(dateField), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.get(dateField), to));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
