package com.sht.zdaicode.ai.model.scheam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 前端专用的精简 Schema 视图模型
 * <p>
 * 从完整的 {@link ProjectScheam} 中提取前端生成所需的最少信息，
 * 剥离所有后端专属字段 (dbType, isAutoIncrement, columnName 等)，
 * 大幅减少传入大模型的上下文 token 数量，降低幻觉概率。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FrontendSchemaView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目简述
     */
    private String description;

    /**
     * 前端实体视图列表
     */
    private List<EntityView> entities;

    // ========== 内部类 ==========

    /**
     * 单个实体的前端视图
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EntityView implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 实体名称 (PascalCase, 如: UserInfo)
         */
        private String entityName;

        /**
         * 实体中文描述 (如: 用户信息)
         */
        private String label;

        /**
         * API 基路径 (如: /api/userInfo)
         * 由转换器根据 tableName 自动推导
         */
        private String apiBasePath;

        /**
         * 主键字段名 (如: id)
         */
        private String primaryKey;

        /**
         * 精简字段列表 (不含主键和自增字段)
         */
        private List<FieldView> fields;
    }

    /**
     * 单个字段的前端视图
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldView implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 字段名 (camelCase, 如: userName)
         */
        private String name;

        /**
         * 中文标签 (如: 用户名)，取自 description
         */
        private String label;

        /**
         * 前端数据类型: string / number / boolean / date / text
         */
        private String type;

        /**
         * 是否必填 (由 isNullable 取反推导)
         */
        private Boolean required;
    }
}
