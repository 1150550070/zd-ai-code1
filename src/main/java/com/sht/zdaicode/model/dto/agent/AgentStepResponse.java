package com.sht.zdaicode.model.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent模式步骤响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepResponse {
    
    /**
     * 步骤编号
     */
    private Integer stepNumber;
    
    /**
     * 步骤名称
     */
    private String stepName;
    
    /**
     * 步骤状态：running, completed, failed
     */
    private String status;
    
    /**
     * 步骤描述
     */
    private String description;
    
    /**
     * 执行结果
     */
    private String result;
    
    /**
     * 是否为最终步骤
     */
    private Boolean isFinal;
    
    /**
     * 时间戳
     */
    private Long timestamp;
}