package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.ai.DatabaseDesignAiService;
import com.sht.zdaicode.ai.DatabaseDesignAiServiceFactory;
import com.sht.zdaicode.ai.model.scheam.EntityScheam;
import com.sht.zdaicode.ai.model.scheam.FieldScheam;
import com.sht.zdaicode.ai.model.scheam.ProjectScheam;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.stream.Collectors;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class DatabaseDesignerNode {
    public static AsyncNodeAction<MessagesState<String>> create() {

        return node_async(state -> {
                    WorkflowContext context = WorkflowContext.getContext(state);
                    log.info("执行节点: 数据库架构设计");

                    try {
                        String userMessage = context.getEnhancedPrompt();
                        if (StrUtil.isBlank(userMessage)) {
                            userMessage = context.getOriginalPrompt();
                        }
                        // 通知前端开始思考数据库架构 (如果支持 SSE 输出)
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n> 🧠 正在分析业务需求，推导全栈数据表结构...\n");
                        }
                        DatabaseDesignAiServiceFactory databaseDesignAiServiceFactory = SpringContextUtil.getBean(DatabaseDesignAiServiceFactory.class);
                        DatabaseDesignAiService databaseDesignAiService = databaseDesignAiServiceFactory.createService();
                        log.info("开始调用 AI 生成数据库 Schema...");
                        ProjectScheam schema = databaseDesignAiService.designDatabase(userMessage);

                        if (schema == null || schema.getEntities() == null || schema.getEntities().isEmpty()) {
                            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 未能成功生成数据库 Schema");
                        }
                        log.info("✅ Schema 设计完成，包含 {} 个数据表", schema.getEntities().size());
                        // 3. 将 JSON Schema 转换为标准的 MySQL DDL 建表语句
                        String sql = generateMySqlDdl(schema);
                        log.debug("生成的 SQL 脚本:\n{}", sql);

                        // 4. 将结果保存到上下文，供后续后端节点、前端节点使用
                        context.setProjectSchema(schema);
                        context.setDatabaseInitSql(sql);

                        // 通知前端数据库设计完成
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept(String.format("\n> ✅ 数据库架构设计完毕，共生成 %d 张数据表。\n", schema.getEntities().size()));
                            // 可以选择性地把生成的 SQL 以 markdown 代码块发给前端展示
                            context.getTokenEmitter().accept("\n```sql\n" + sql + "\n```\n");
                        }
                    } catch (Exception e) {
                        log.error("数据库架构设计异常: {}", e.getMessage(), e);
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n\n❌ [架构设计失败: " + e.getMessage() + "]\n");
                        }
                        // 全栈模式下，数据库设计失败是致命的，直接抛出异常中断工作流
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据库架构设计失败: " + e.getMessage());
                    }

                    // 更新当前步骤状态并扭转到下一个节点
                    context.setCurrentStep("数据库架构设计");
                    return WorkflowContext.saveContext(context);
                }

        );
    }

    /**
     * 内部辅助方法：根据 Schema 对象生成 MySQL DDL 语句
     */
    private static String generateMySqlDdl(ProjectScheam schema) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("-- --------------------------------------------------------\n");
        sqlBuilder.append("-- 自动生成的数据库初始化脚本\n");
        sqlBuilder.append("-- 项目名称: ").append(schema.getProjectName()).append("\n");
        sqlBuilder.append("-- 项目描述: ").append(schema.getDescription() != null ? schema.getDescription() : "无").append("\n");
        sqlBuilder.append("-- --------------------------------------------------------\n\n");

        for (EntityScheam entity : schema.getEntities()) {
            sqlBuilder.append(String.format("DROP TABLE IF EXISTS `%s`;\n", entity.getTableName()));
            sqlBuilder.append(String.format("CREATE TABLE `%s` (\n", entity.getTableName()));

            // 遍历并生成所有字段
            String fieldsSql = entity.getFields().stream()
                    .map(DatabaseDesignerNode::buildColumnDefinition)
                    .collect(Collectors.joining(",\n"));
            sqlBuilder.append(fieldsSql);

            // 追加主键定义
            entity.getFields().stream()
                    .filter(field -> Boolean.TRUE.equals(field.getIsPrimaryKey()))
                    .findFirst()
                    .ifPresent(pk -> sqlBuilder.append(String.format(",\n  PRIMARY KEY (`%s`)", pk.getColumnName())));

            String tableComment = StrUtil.isNotBlank(entity.getDescription()) ? entity.getDescription() : entity.getTableName();
            sqlBuilder.append(String.format("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='%s';\n\n", tableComment));
        }
        return sqlBuilder.toString();
    }

    /**
     * 构建单个字段的 SQL 定义
     */
    private static String buildColumnDefinition(FieldScheam field) {
        StringBuilder col = new StringBuilder();
        col.append("  `").append(field.getColumnName()).append("` ").append(field.getDbType());

        if (!Boolean.TRUE.equals(field.getIsNullable())) {
            col.append(" NOT NULL");
        }
        if (Boolean.TRUE.equals(field.getIsAutoIncrement())) {
            col.append(" AUTO_INCREMENT");
        }
        if (StrUtil.isNotBlank(field.getDescription())) {
            col.append(" COMMENT '").append(field.getDescription()).append("'");
        }
        return col.toString();
    }
}
