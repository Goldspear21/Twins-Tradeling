package com.twins.Tradeling.repository;

import com.twins.Tradeling.model.Replenishment;
import org.springframework.data.jpa.repository.*;

public interface ReplenishmentRepository extends JpaRepository<Replenishment, Long>, JpaSpecificationExecutor<Replenishment> {
    boolean existsByReference(String reference);
}
