package com.kingwiredemo.repository;

import com.kingwiredemo.model.entity.Pricing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PricingRepository extends JpaRepository<Pricing, Long> {

    Page<Pricing> findByPriceType(String priceType, Pageable pageable);

    Optional<Pricing> findByProductIdAndPriceType(Long productId, String priceType);
}
