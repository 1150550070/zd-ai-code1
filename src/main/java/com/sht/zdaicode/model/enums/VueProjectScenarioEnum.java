package com.sht.zdaicode.model.enums;

import lombok.Getter;

/**
 * Vue项目场景枚举
 */
@Getter
public enum VueProjectScenarioEnum {

    /**
     * 创建模式：从零开始创建Vue项目
     */
    CREATE("create", "创建模式"),

    /**
     * 修改模式：修改已存在的Vue项目
     */
    EDIT("edit", "修改模式");

    private final String value;
    private final String text;

    VueProjectScenarioEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据值获取枚举
     */
    public static VueProjectScenarioEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (VueProjectScenarioEnum anEnum : VueProjectScenarioEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}