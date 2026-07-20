package com.campus.dto;

import lombok.Data;

@Data
public class UpdateItemDTO {

    private String name;

    private Long categoryId;

    private String images;

    private String description;

    private Long price;

    private Long originalPrice;

    private String campus;

    private String meetPlace;

    private Double x;

    private Double y;

    private Integer itemCondition;
}