package com.kingwiredemo.repository;

public interface ProductSummaryProjection {
    String getSource();
    String getProductLine();
    Long getCount();
}
