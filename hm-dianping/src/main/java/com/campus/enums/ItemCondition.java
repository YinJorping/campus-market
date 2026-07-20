package com.campus.enums;

/**
 * 商品新旧程度枚举
 */
public enum ItemCondition {

    BRAND_NEW(1, "全新"),
    LIKE_NEW(2, "几乎全新"),
    GOOD(3, "良好"),
    FAIR(4, "一般"),
    FLAWED(5, "有瑕疵");

    private final int value;
    private final String desc;

    ItemCondition(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static ItemCondition fromValue(int value) {
        for (ItemCondition condition : ItemCondition.values()) {
            if (condition.value == value) {
                return condition;
            }
        }
        throw new IllegalArgumentException("Unknown ItemCondition value: " + value);
    }
}
