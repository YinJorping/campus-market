package com.campus.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CreateOrderDTO {

    @NotNull(message = "商品ID不能为空")
    private Long itemId;
}
