package com.campus.vo;

import lombok.Data;

/**
 * 分类列表返回 VO
 */
@Data
public class CategoryVO {
    private Long id;
    private String name;
    private String icon;
    private Integer sort;
}
