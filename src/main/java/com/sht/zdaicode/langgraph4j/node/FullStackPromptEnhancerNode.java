package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.langgraph4j.model.ImageResource;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class FullStackPromptEnhancerNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 全栈提示词增强");
            
            // 全栈工作流中，我们使用经过需求分析节点扩充后的提示词，如果没有，回退到原始提示词
            String basePrompt = context.getEnhancedPrompt() != null ? context.getEnhancedPrompt() : context.getOriginalPrompt();
            
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();
            
            // 构建增强后的提示词
            StringBuilder enhancedPromptBuilder = new StringBuilder();
            enhancedPromptBuilder.append(basePrompt);
            
            // 预估静态文本长度 (标题 + 说明 + 示例) 约 400 字符
            int staticTextLength = 400;
            // 剩余可用长度 (总长度限制 5000 - 基础提示词 - 静态文本预留)
            int remainingLength = 5000 - basePrompt.length() - staticTextLength;

            // 如果有图片资源且有剩余空间，则添加图片信息
            if ((CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)) && remainingLength > 0) {
                enhancedPromptBuilder.append("\n\n## 可用素材资源\n");
                enhancedPromptBuilder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
                // 添加国内可访问的示例格式说明
                enhancedPromptBuilder.append("\n拼接的提示词示例：\n");
                enhancedPromptBuilder.append("- 架构图：阿里云技术架构参考（https://img.alicdn.com/tfs/TB1Ly5oS3HqK1RjSZFPXXcwapXa-2872-1579.png）\n");
                enhancedPromptBuilder.append("- Logo图片：码云Gitee平台Logo（https://gitee.com/static/images/logo_gitee_red.svg）\n");
                enhancedPromptBuilder.append("- 界面截图：Ant Design组件库界面（https://gw.alipayobjects.com/zos/rmsportal/KDpgvguMpGfqaHPjicRK.svg）\n");
                enhancedPromptBuilder.append("- 技术图标：掘金技术社区图标（https://lf3-cdn-tos.bytescm.com/obj/static/xitu_juejin_web/static/favicons/favicon-32x32.png）\n\n");

                if (CollUtil.isNotEmpty(imageList)) {
                    for (ImageResource image : imageList) {
                        // 构建单个图片描述
                        StringBuilder imageDescBuilder = new StringBuilder();
                        imageDescBuilder.append("- ")
                                .append(image.getCategory().getText())
                                .append("：")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");

                        String imageDesc = imageDescBuilder.toString();
                        // 检查添加此图片后是否会超长 (预留 50 字符缓冲)
                        if (enhancedPromptBuilder.length() + imageDesc.length() < 4950) {
                            enhancedPromptBuilder.append(imageDesc);
                        } else {
                            log.warn("图片列表过长，已截断后续图片以满足护轨长度限制");
                            break;
                        }
                    }
                } else {
                    // 兼容旧字段，但也要检查长度
                    String listStr = imageListStr;
                    if (enhancedPromptBuilder.length() + listStr.length() < 4950) {
                        enhancedPromptBuilder.append(listStr);
                    }
                }
            }

            String finalEnhancedPrompt = enhancedPromptBuilder.toString();
            // 双重保险：如果仍然超过 5000，强制截断
            if (finalEnhancedPrompt.length() > 5000) {
                finalEnhancedPrompt = finalEnhancedPrompt.substring(0, 5000);
                log.warn("增强后提示词超过5000字符，已强制截断");
            }
            
            // 更新状态
            context.setCurrentStep("全栈提示词素材注入");
            context.setEnhancedPrompt(finalEnhancedPrompt);
            log.info("提示词增强完成，增强后长度: {} 字符", finalEnhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }
}
