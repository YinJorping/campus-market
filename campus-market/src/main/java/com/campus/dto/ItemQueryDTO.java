package com.campus.dto;

import lombok.Data;

@Data
public class ItemQueryDTO {

    private Integer current = 1;

    private Integer pageSize = 10;

    private Long categoryId;

    private String campus;

    private Integer condition;

    private String keyword;

    private Long priceMin;

    private Long priceMax;

    private String sort = "newest";
}
