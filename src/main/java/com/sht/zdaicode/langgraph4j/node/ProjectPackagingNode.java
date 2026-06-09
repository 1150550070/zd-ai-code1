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

/**
 * 全栈应用：项目组装与打包节点
 * 负责将生成的 SQL、后端代码、前端代码合并到一个统一的全栈目录中
 */
@Slf4j
public class ProjectPackagingNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("📦 执行节点: 组装全栈统一项目目录");

            Long appId = context.getAppId() != null ? context.getAppId() : 0L;

            // 1. 定义统一的全栈根目录 (例如: /workspace/fullstack_app_1001)
            String unifiedDir = String.format("%s/fullstack_app_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, appId);
            FileUtil.mkdir(unifiedDir);

            try {
                // 通知前端
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n> 📦 正在组装全栈项目，生成统一工程目录...\n");
                }

                // 2. 写入数据库 SQL 脚本
                if (StrUtil.isNotBlank(context.getDatabaseInitSql())) {
                    String sqlFilePath = unifiedDir + "/database/schema.sql";
                    FileUtil.writeUtf8String(context.getDatabaseInitSql(), sqlFilePath);
                    log.info("💾 SQL 脚本已保存至: {}", sqlFilePath);
                }

                // 3. 移动后端代码 (将原后端目录移动到 unifiedDir/backend)
                String backendDir = context.getBackendCodeDir();
                if (StrUtil.isNotBlank(backendDir) && FileUtil.exist(backendDir)) {
                    File targetBackendDir = new File(unifiedDir + "/backend");
                    FileUtil.move(new File(backendDir), targetBackendDir, true);
                    log.info("☕ 后端代码已组装至: {}", targetBackendDir.getAbsolutePath());
                }

                // 4. 移动前端代码 (将原前端目录移动到 unifiedDir/frontend)
                String frontendDir = context.getFrontendCodeDir();
                if (StrUtil.isNotBlank(frontendDir) && FileUtil.exist(frontendDir)) {
                    File targetFrontendDir = new File(unifiedDir + "/frontend");
                    FileUtil.move(new File(frontendDir), targetFrontendDir, true);
                    log.info("💻 前端代码已组装至: {}", targetFrontendDir.getAbsolutePath());

                    // 【非常关键】：将工作流的目标构建目录，更新为新组装好的前端目录！
                    // 这样后续的 ProjectBuilderNode (npm run build) 才能在正确的目录下执行
                    context.setGeneratedCodeDir(targetFrontendDir.getAbsolutePath());
                }

                context.setUnifiedProjectDir(unifiedDir);
                context.setCurrentStep("项目组装");

                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ✅ 全栈工程组装完毕！\n");
                }

            } catch (Exception e) {
                log.error("项目组装失败: {}", e.getMessage(), e);
                throw new RuntimeException("全栈项目组装失败", e);
            }

            return WorkflowContext.saveContext(context);
        });
    }
}