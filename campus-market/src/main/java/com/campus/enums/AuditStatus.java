package com.campus.enums;

/**
 * 审核状态枚举
 */
public enum AuditStatus {

    PENDING(0, "待审核"),
    APPROVED(1, "审核通过"),
    REJECTED(2, "审核不通过");

    private final int value;
    private final String desc;

    AuditStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static AuditStatus fromValue(int value) {
        for (AuditStatus status : AuditStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AuditStatus value: " + value);
    }
}
