package com.kingwiredemo.api;

import com.kingwiredemo.model.entity.Pricing;
import com.kingwiredemo.repository.PricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingRepository pricingRepository;

    /** Pricing records by type (LIST | DISTRIBUTOR | CONTRACT). Paginated. */
    @GetMapping
    public Page<Pricing> getPricing(
            @RequestParam(required = false) String type,
            @PageableDefault(size = 50) Pageable pageable) {

        return type != null
                ? pricingRepository.findByPriceType(type.toUpperCase(), pageable)
                : pricingRepository.findAll(pageable);
    }
}
