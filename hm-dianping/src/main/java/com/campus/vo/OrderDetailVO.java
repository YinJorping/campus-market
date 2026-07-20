package com.campus.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailVO {

    private Long id;

    private Long itemId;

    private String itemName;

    private String itemCoverImage;

    private List<String> itemImages;

    private Long price;

    private Integer status;

    private Long buyerId;

    private String buyerName;

    private String buyerAvatar;

    private Long sellerId;

    private String sellerName;

    private String sellerAvatar;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
