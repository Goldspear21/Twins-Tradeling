package com.twins.Tradeling.repository;

import com.twins.Tradeling.model.PlanningScenario;
import org.springframework.data.jpa.repository.*;

public interface PlanningScenarioRepository extends JpaRepository<PlanningScenario, Long>, JpaSpecificationExecutor<PlanningScenario> {
}
