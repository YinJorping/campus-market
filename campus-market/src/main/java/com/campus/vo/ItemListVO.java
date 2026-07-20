package com.campus.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItemListVO {

    private Long id;

    private String name;

    private String coverImage;

    private Long price;

    private Long originalPrice;

    private String categoryName;

    private String sellerName;

    private String campus;

    private Integer condition;

    private Integer viewCount;

    private LocalDateTime createTime;
}
