package com.sht.zdaicode.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiContract {

    /**
     * API 基础路径，例如: /api/v1
     */
    private String basePath;

    /**
     * 接口列表
     */
    private List<ApiEndpoint> endpoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpoint {
        /**
         * 接口描述，例如：获取用户列表
         */
        private String description;

        /**
         * 请求方法，例如: GET, POST, PUT, DELETE
         */
        private String method;

        /**
         * 接口路径，例如: /users
         */
        private String path;

        /**
         * 请求参数说明 (可选)
         */
        private List<ApiParameter> requestParameters;

        /**
         * 响应数据说明 (可选)
         */
        private List<ApiParameter> responseFields;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiParameter {
        /**
         * 字段名称
         */
        private String name;

        /**
         * 字段类型，例如: string, integer, boolean
         */
        private String type;

        /**
         * 字段描述
         */
        private String description;

        /**
         * 是否必须
         */
        private Boolean required;
    }
}
