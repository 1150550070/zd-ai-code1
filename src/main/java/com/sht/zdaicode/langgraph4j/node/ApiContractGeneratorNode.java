package com.sht.zdaicode.langgraph4j.node;

import com.sht.zdaicode.ai.ApiContractAiService;
import com.sht.zdaicode.ai.ApiContractAiServiceFactory;
import com.sht.zdaicode.ai.model.ApiContract;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ApiContractGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: API 契约生成");

            try {
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> 📜 正在生成前后端交互 API 契约...\n");
                }

                ApiContractAiServiceFactory factory = SpringContextUtil.getBean(ApiContractAiServiceFactory.class);
                ApiContractAiService aiService = factory.createService();
                
                String input = "需求分析:\n" + context.getAnalyzedRequirements() + "\n\n数据库设计 SQL:\n" + context.getDatabaseInitSql();
                
                log.info("开始调用 AI 生成 API 契约...");
                ApiContract apiContract = aiService.generateApiContract(input);

                if (apiContract == null || apiContract.getEndpoints() == null || apiContract.getEndpoints().isEmpty()) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "API 契约生成失败");
                }
                
                log.info("API 契约生成完成，共包含 {} 个接口", apiContract.getEndpoints().size());
                context.setApiContract(apiContract);

                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ✅ API 契约生成完成，共 " + apiContract.getEndpoints().size() + " 个接口。\n");
                }
            } catch (Exception e) {
                log.error("API 契约生成异常: {}", e.getMessage(), e);
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n❌ [API 契约生成失败: " + e.getMessage() + "]\n");
                }
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "API 契约生成失败: " + e.getMessage());
            }

            context.setCurrentStep("API契约生成");
            return WorkflowContext.saveContext(context);
        });
    }
}
