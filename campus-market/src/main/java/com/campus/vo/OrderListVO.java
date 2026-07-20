package com.campus.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderListVO {

    private Long id;

    private Long itemId;

    private String itemName;

    private String itemCoverImage;

    private Long price;

    private Integer status;

    private String counterpartyName;

    private LocalDateTime createTime;
}
