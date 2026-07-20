package com.campus.enums;

/**
 * 订单状态枚举
 */
public enum OrderStatus {

    PENDING(1, "待确认"),
    CONFIRMED(2, "已确认"),
    FINISHED(3, "已完成"),
    REJECTED(4, "已拒绝"),
    CANCELLED(5, "已取消");

    private final int value;
    private final String desc;

    OrderStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static OrderStatus fromValue(int value) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus value: " + value);
    }
}
