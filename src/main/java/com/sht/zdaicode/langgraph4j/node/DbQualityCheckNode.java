package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.langgraph4j.ai.DbQualityCheckService;
import com.sht.zdaicode.langgraph4j.model.QualityResult;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class DbQualityCheckNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 数据库质量检查");
            QualityResult qualityResult;

            try {
                String dbSql = context.getDatabaseInitSql();
                if (StrUtil.isBlank(dbSql)) {
                    log.warn("未找到可检查的数据库SQL");
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的数据库SQL"))
                            .suggestions(List.of("请确保数据库架构生成成功"))
                            .build();
                } else {
                    if (context.getTokenEmitter() != null) {
                        context.getTokenEmitter().accept("\n> 🔍 正在进行数据库 Schema 质检...\n");
                    }
                    DbQualityCheckService qualityCheckService = SpringContextUtil.getBean(DbQualityCheckService.class);
                    // 数据库专用质检接口
                    qualityResult = qualityCheckService.checkDbQuality("请检查以下 MySQL DDL 语句的正确性:\n```sql\n" + dbSql + "\n```");
                    log.info("数据库质检完成 - 是否通过: {}", qualityResult.getIsValid());
                }
            } catch (Exception e) {
                log.error("数据库质检异常: {}", e.getMessage(), e);
                qualityResult = QualityResult.builder()
                        .isValid(false)
                        .errors(List.of(e.getMessage()))
                        .build();
            }

            if (!qualityResult.getIsValid()) {
                context.setDbDesignRetryCount(context.getDbDesignRetryCount() + 1);
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ⚠️ 数据库质检未通过，进行重试 (已重试 " + context.getDbDesignRetryCount() + " 次)\n");
                }
            } else {
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ✅ 数据库质检通过。\n");
                }
            }

            context.setDbQualityResult(qualityResult);
            context.setCurrentStep("数据库质检");
            return WorkflowContext.saveContext(context);
        });
    }
}
