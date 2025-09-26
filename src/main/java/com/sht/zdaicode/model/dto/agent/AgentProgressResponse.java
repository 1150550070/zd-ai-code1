package com.sht.zdaicode.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent模式进度响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentProgressResponse {
    
    /**
     * 响应类型：step, progress, complete, error
     */
    private String type;
    
    /**
     * 总步骤数
     */
    private Integer totalSteps;
    
    /**
     * 当前步骤
     */
    private Integer currentStep;
    
    /**
     * 整体进度百分比
     */
    private Integer progressPercentage;
    
    /**
     * 当前步骤信息
     */
    private AgentStepResponse currentStepInfo;
    
    /**
     * 所有步骤列表
     */
    private List<AgentStepResponse> allSteps;
    
    /**
     * 消息内容
     */
    private String message;
    
    /**
     * 是否完成
     */
    private Boolean isComplete;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 时间戳
     */
    private Long timestamp;
}