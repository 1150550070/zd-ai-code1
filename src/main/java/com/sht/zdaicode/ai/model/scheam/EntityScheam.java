package com.sht.zdaicode.ai.model.scheam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityScheam implements Serializable {
    // 实体类名 (PascalCase, 如: UserInfo)
    private String className;
    // 数据库表名 (snake_case, 如: user_info)
    private String tableName;
    // 实体描述 (如: 用户信息表)
    private String description;
    // 字段列表
    private List<FieldScheam> fields;

    private static final long serialVersionUID = 1L;
}
