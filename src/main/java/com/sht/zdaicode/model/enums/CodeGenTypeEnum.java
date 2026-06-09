package com.sht.zdaicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 代码生成类型枚举
 */
@Getter
public enum CodeGenTypeEnum {

    HTML("原生 HTML 模式", "html"),
    MULTI_FILE("原生多文件模式", "multi_file"),
    VUE_PROJECT_CREATE("Vue 工程创建模式", "vue_project_create"),
    VUE_PROJECT_EDIT("Vue 工程编辑模式", "vue_project_edit"),
    BACKEND_PROJECT_CREATE("java后端创建模式", "backend_project_create"),
    BACKEND_PROJECT_EDIT("java后端编辑模式", "backend_project_edit"),
    FRONTEND_FULLSTACK("全栈前端应用生成", "frontend_fullstack"),
    FULLSTACK("全栈模式", "fullstack");


    private final String text;
    private final String value;

    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static CodeGenTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (CodeGenTypeEnum anEnum : CodeGenTypeEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
