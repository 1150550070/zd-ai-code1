package com.sht.zdaicode.ai.model.scheam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldScheam implements Serializable {
    // 实体属性名 (camelCase, 如: userName)
    private String name;
    // 数据库列名 (snake_case, 如: user_name)
    private String columnName;
    // Java 数据类型 (如: String, Long, Integer, LocalDateTime)
    private String javaType;
    // 数据库数据类型 (如: VARCHAR(255), BIGINT, INT, DATETIME)
    private String dbType;
    // 字段中文描述
    private String description;
    // 是否为主键
    private Boolean isPrimaryKey;
    // 是否自增
    private Boolean isAutoIncrement;
    // 是否允许为空
    private Boolean isNullable;

    private static final long serialVersionUID = 1L;
}
