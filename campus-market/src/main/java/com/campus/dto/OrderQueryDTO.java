package com.campus.dto;

import lombok.Data;

@Data
public class OrderQueryDTO {

    private Integer current = 1;

    private Integer pageSize = 10;

    private Integer status;
}
