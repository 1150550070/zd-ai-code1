package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class FullStackConvergenceNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 全栈构建汇聚");

            // 由于前端和后端可能并行完成，此节点作为同步点
            // 可以做一些目录合并或者日志记录的工作
            
            // 如果某一方还未完成（在支持真实并行的引擎中），可以选择记录日志或做等待
            if (!context.isFrontendDone() || !context.isBackendDone()) {
                log.info("提示: 全栈汇聚中, 前端完成状态={}, 后端完成状态={}", context.isFrontendDone(), context.isBackendDone());
            }

            Long appId = context.getAppId() != null ? context.getAppId() : 0L;

            // 1. 定义统一的全栈根目录 (例如: /workspace/fullstack_app_1001)
            String unifiedDir = String.format("%s/fullstack_app_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, appId);
            FileUtil.mkdir(unifiedDir);

            try {
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> 🔄 前后端代码生成及质检完成，正在组装全栈统一工程目录...\n");
                }

                // 2. 写入数据库 SQL 脚本
                if (StrUtil.isNotBlank(context.getDatabaseInitSql())) {
                    String sqlFilePath = unifiedDir + "/database/schema.sql";
                    FileUtil.writeUtf8String(context.getDatabaseInitSql(), sqlFilePath);
                    log.info("💾 SQL 脚本已保存至: {}", sqlFilePath);
                }

                // 3. 移动后端代码 (将原后端目录移动到 unifiedDir/backend)
                String backendDir = context.getBackendGeneratedCodeDir();
                if (StrUtil.isNotBlank(backendDir) && FileUtil.exist(backendDir)) {
                    File targetBackendDir = new File(unifiedDir + "/backend");
                    FileUtil.move(new File(backendDir), targetBackendDir, true);
                    log.info("☕ 后端代码已组装至: {}", targetBackendDir.getAbsolutePath());
                }

                // 4. 移动前端代码 (将原前端目录移动到 unifiedDir/frontend)
                String frontendDir = context.getFrontendGeneratedCodeDir();
                if (StrUtil.isNotBlank(frontendDir) && FileUtil.exist(frontendDir)) {
                    File targetFrontendDir = new File(unifiedDir + "/frontend");
                    FileUtil.move(new File(frontendDir), targetFrontendDir, true);
                    log.info("💻 前端代码已组装至: {}", targetFrontendDir.getAbsolutePath());
                }

                // 【非常关键】：将工作流的目标构建目录更新为全栈统一目录！
                // 这样后续的 ProjectBuilderNode 可以在全栈根目录下分别执行前端和后端的构建
                context.setGeneratedCodeDir(unifiedDir);

                context.setCurrentStep("全栈构建汇聚");

                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ✅ 全栈工程组装完毕！准备进入项目构建阶段...\n");
                }

            } catch (Exception e) {
                log.error("全栈项目组装失败: {}", e.getMessage(), e);
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ❌ 全栈工程组装异常: " + e.getMessage() + "\n");
                }
            }
            
            return WorkflowContext.saveContext(context);
        });
    }
}
