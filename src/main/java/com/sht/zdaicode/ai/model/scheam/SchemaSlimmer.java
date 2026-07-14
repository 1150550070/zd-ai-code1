package com.sht.zdaicode.ai.model.scheam;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Schema 精炼器 — 将完整的后端 {@link ProjectScheam} 转换为前端专用的 {@link FrontendSchemaView}
 * <p>
 * 核心目标：
 * <ul>
 *   <li>剥离所有后端专属字段 (dbType, isAutoIncrement, columnName, isNullable 等)</li>
 *   <li>将 Java 类型映射为前端友好的类型 (string/number/boolean/date/text)</li>
 *   <li>自动推导 API 基路径 (/api/entityName)</li>
 *   <li>将主键字段单独提取，普通字段列表中不再重复出现</li>
 * </ul>
 * 转换后的 JSON 体积通常为原始 Schema 的 20%-30%，显著降低 LLM 上下文噪声。
 * </p>
 */
@Slf4j
public class SchemaSlimmer {

    private SchemaSlimmer() {
        // 工具类禁止实例化
    }

    /**
     * 将完整的 ProjectScheam 转换为前端精简视图
     *
     * @param fullSchema 完整的后端 Schema
     * @return 前端精简 Schema 视图
     */
    public static FrontendSchemaView slim(ProjectScheam fullSchema) {
        if (fullSchema == null) {
            log.warn("传入的 ProjectScheam 为 null，返回空的 FrontendSchemaView");
            return FrontendSchemaView.builder()
                    .projectName("unknown")
                    .entities(List.of())
                    .build();
        }

        List<FrontendSchemaView.EntityView> entityViews = new ArrayList<>();

        if (CollUtil.isNotEmpty(fullSchema.getEntities())) {
            for (EntityScheam entity : fullSchema.getEntities()) {
                entityViews.add(convertEntity(entity));
            }
        }

        FrontendSchemaView view = FrontendSchemaView.builder()
                .projectName(fullSchema.getProjectName())
                .description(fullSchema.getDescription())
                .entities(entityViews)
                .build();

        log.info("Schema 精炼完成: {} 个实体, {} 个字段 → 前端视图",
                entityViews.size(),
                entityViews.stream().mapToInt(e -> CollUtil.size(e.getFields())).sum());

        return view;
    }

    /**
     * 转换单个实体
     */
    private static FrontendSchemaView.EntityView convertEntity(EntityScheam entity) {
        String entityName = entity.getClassName();
        String apiBasePath = deriveApiBasePath(entity.getTableName(), entityName);
        String primaryKey = null;

        List<FrontendSchemaView.FieldView> fieldViews = new ArrayList<>();

        if (CollUtil.isNotEmpty(entity.getFields())) {
            for (FieldScheam field : entity.getFields()) {
                // 提取主键
                if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                    primaryKey = field.getName();
                    // 主键字段不放入普通字段列表，减少冗余
                    continue;
                }
                // 跳过自增字段 (通常是主键的伴随字段)
                if (Boolean.TRUE.equals(field.getIsAutoIncrement())) {
                    continue;
                }
                fieldViews.add(convertField(field));
            }
        }

        return FrontendSchemaView.EntityView.builder()
                .entityName(entityName)
                .label(entity.getDescription())
                .apiBasePath(apiBasePath)
                .primaryKey(primaryKey != null ? primaryKey : "id")
                .fields(fieldViews)
                .build();
    }

    /**
     * 转换单个字段
     */
    private static FrontendSchemaView.FieldView convertField(FieldScheam field) {
        return FrontendSchemaView.FieldView.builder()
                .name(field.getName())
                .label(StrUtil.isNotBlank(field.getDescription()) ? field.getDescription() : field.getName())
                .type(mapToFrontendType(field.getJavaType(), field.getDbType()))
                .required(!Boolean.TRUE.equals(field.getIsNullable()))
                .build();
    }

    /**
     * 根据 tableName 或 className 推导 API 基路径
     * <p>
     * 规则: user_info → /api/userInfo
     * </p>
     */
    private static String deriveApiBasePath(String tableName, String className) {
        if (StrUtil.isNotBlank(className)) {
            // 将 PascalCase 转为 camelCase 作为路径: UserInfo → userInfo
            String camel = className.substring(0, 1).toLowerCase() + className.substring(1);
            return "/api/" + camel;
        }
        if (StrUtil.isNotBlank(tableName)) {
            // 将 snake_case 转为 camelCase: user_info → userInfo
            return "/api/" + StrUtil.toCamelCase(tableName);
        }
        return "/api/unknown";
    }

    /**
     * 将 Java/DB 类型映射为前端友好类型
     *
     * @param javaType Java 类型 (如 String, Long, LocalDateTime)
     * @param dbType   数据库类型 (如 VARCHAR(255), TEXT, DATETIME)
     * @return 前端类型: string / number / boolean / date / text
     */
    private static String mapToFrontendType(String javaType, String dbType) {
        // 优先根据 Java 类型判断
        if (StrUtil.isNotBlank(javaType)) {
            String type = javaType.toLowerCase();
            if (type.contains("long") || type.contains("integer") || type.contains("int")
                    || type.contains("double") || type.contains("float") || type.contains("bigdecimal")
                    || type.contains("short") || type.contains("byte")) {
                return "number";
            }
            if (type.contains("boolean")) {
                return "boolean";
            }
            if (type.contains("date") || type.contains("time") || type.contains("localdate")
                    || type.contains("localdatetime") || type.contains("timestamp")) {
                return "date";
            }
        }

        // 辅助: 根据 DB 类型判断 (兜底逻辑)
        if (StrUtil.isNotBlank(dbType)) {
            String db = dbType.toUpperCase();
            if (db.contains("TEXT") || db.contains("LONGTEXT") || db.contains("MEDIUMTEXT")) {
                return "text";
            }
            if (db.contains("INT") || db.contains("BIGINT") || db.contains("DECIMAL")
                    || db.contains("FLOAT") || db.contains("DOUBLE") || db.contains("NUMERIC")) {
                return "number";
            }
            if (db.contains("BOOL") || db.contains("TINYINT(1)")) {
                return "boolean";
            }
            if (db.contains("DATE") || db.contains("TIME") || db.contains("TIMESTAMP")) {
                return "date";
            }
        }

        // 默认为 string
        return "string";
    }
}
