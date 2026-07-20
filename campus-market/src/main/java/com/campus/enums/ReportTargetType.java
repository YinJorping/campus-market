package com.campus.enums;

/**
 * 举报目标类型枚举
 */
public enum ReportTargetType {

    ITEM(1, "商品"),
    USER(2, "用户");

    private final int value;
    private final String desc;

    ReportTargetType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static ReportTargetType fromValue(int value) {
        for (ReportTargetType type : ReportTargetType.values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ReportTargetType value: " + value);
    }
}
