package com.campus.enums;

/**
 * 商品状态枚举
 */
public enum ItemStatus {

    ON_SALE(1, "在售"),
    RESERVED(2, "已预约"),
    SOLD(3, "已售"),
    OFF_SHELF(4, "下架");

    private final int value;
    private final String desc;

    ItemStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static ItemStatus fromValue(int value) {
        for (ItemStatus status : ItemStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ItemStatus value: " + value);
    }
}
