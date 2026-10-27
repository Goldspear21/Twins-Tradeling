package com.twins.Tradeling.repository;

import com.twins.Tradeling.model.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.sku = :sku")
    Optional<Product> findBySkuForUpdate(@Param("sku") String sku);
}
