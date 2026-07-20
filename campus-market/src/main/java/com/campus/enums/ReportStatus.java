package com.campus.enums;

/**
 * 举报处理状态枚举
 */
public enum ReportStatus {

    PENDING(0, "待处理"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回");

    private final int value;
    private final String desc;

    ReportStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }

    public static ReportStatus fromValue(int value) {
        for (ReportStatus status : ReportStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ReportStatus value: " + value);
    }
}
