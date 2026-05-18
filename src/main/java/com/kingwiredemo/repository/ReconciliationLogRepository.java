package com.kingwiredemo.repository;

import com.kingwiredemo.model.entity.ReconciliationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {

    Page<ReconciliationLog> findBySku(String sku, Pageable pageable);

    Page<ReconciliationLog> findByAction(String action, Pageable pageable);

    long countByAction(String action);
}
