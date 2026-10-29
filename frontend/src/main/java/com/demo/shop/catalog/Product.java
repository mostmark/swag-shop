package com.demo.shop.catalog;

import java.math.BigDecimal;
import java.util.List;

public record Product(
        String id,
        String name,
        String category,
        String description,
        BigDecimal price,
        String image,
        List<String> tags) {
}
