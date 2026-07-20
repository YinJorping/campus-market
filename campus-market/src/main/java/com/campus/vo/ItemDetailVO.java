package com.campus.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ItemDetailVO {

    private Long id;

    private String name;

    private List<String> images;

    private String description;

    private Long price;

    private Long originalPrice;

    private String campus;

    private String meetPlace;

    private Double x;

    private Double y;

    private Integer itemCondition;

    private Integer viewCount;

    private LocalDateTime createTime;

    private LocalDateTime soldTime;

    private String categoryName;

    private Long sellerId;

    private String sellerName;

    private String sellerAvatar;

    private Boolean isFavorite;

    private Integer sellerOtherCount;
}
