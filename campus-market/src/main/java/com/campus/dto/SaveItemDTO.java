package com.campus.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Data
public class SaveItemDTO {

    @NotBlank(message = "商品名称不能为空")
    private String name;

    @NotNull(message = "请选择商品分类")
    private Long categoryId;

    @NotBlank(message = "请上传至少一张商品图片")
    private String images;

    private String description;

    @NotNull(message = "请输入价格")
    @Positive(message = "价格必须大于0")
    private Long price;

    private Long originalPrice;

    @NotBlank(message = "请选择校区")
    private String campus;

    @NotBlank(message = "请填写交易地点")
    private String meetPlace;

    private Double x;

    private Double y;

    @NotNull(message = "请选择商品成色")
    private Integer itemCondition;
}