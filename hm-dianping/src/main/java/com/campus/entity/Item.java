package com.campus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_item")
public class Item implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 卖家用户ID
     */
    private Long sellerId;

    /**
     * 商品图片，多张以逗号分隔
     */
    private String images;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 售价，单位分
     */
    private Long price;

    /**
     * 原价/参考价，单位分
     */
    private Long originalPrice;

    /**
     * 校区/生活区
     */
    private String campus;

    /**
     * 交易地点
     */
    private String meetPlace;

    /**
     * 经度
     */
    private Double x;

    /**
     * 纬度
     */
    private Double y;

    /**
     * 新旧程度：1全新 2几乎全新 3良好 4一般 5有瑕疵
     */
    private Integer itemCondition;

    /**
     * 商品状态：1在售 2已预约 3已售 4下架
     */
    private Integer status;

    /**
     * 审核状态：0待审核 1审核通过 2审核不通过
     */
    private Integer auditStatus;

    /**
     * 逻辑删除：0未删除 1已删除
     */
    private Boolean deleted;

    /**
     * 浏览次数
     */
    private Integer viewCount;

    /**
     * 咨询量
     */
    private Integer consultCount;

    /**
     * 成交时间
     */
    private LocalDateTime soldTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 距离（GEO查询结果）
     */
    @TableField(exist = false)
    private Double distance;
}
