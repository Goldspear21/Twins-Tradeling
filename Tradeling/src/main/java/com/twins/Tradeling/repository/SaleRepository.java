package com.twins.Tradeling.repository;

import com.twins.Tradeling.model.Sale;
import com.twins.Tradeling.model.DomainTypes.Warehouse;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    boolean existsByReference(String reference);

    @Query("select coalesce(sum(s.quantity), 0) from Sale s where s.product.sku = :sku and s.warehouse = :warehouse and s.soldAt >= :from and s.soldAt < :to")
    long quantitySold(@Param("sku") String sku, @Param("warehouse") Warehouse warehouse,
                      @Param("from") Instant from, @Param("to") Instant to);

    List<Sale> findBySoldAtGreaterThanEqualAndSoldAtLessThan(Instant from, Instant to);
}
