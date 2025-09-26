package com.sht.zdaicode.controller;

import com.sht.zdaicode.ai.VueProjectAiServiceFactory;
import com.sht.zdaicode.ai.VueProjectScenarioDetector;
import com.sht.zdaicode.model.enums.VueProjectScenarioEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 测试控制器 - 用于验证Vue项目架构优化
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private VueProjectScenarioDetector vueProjectScenarioDetector;

    @Autowired
    private VueProjectAiServiceFactory vueProjectAiServiceFactory;

    /**
     * 测试Vue项目工具集配置
     */
    @GetMapping("/vue-tools")
    public String testVueToolsConfiguration(@RequestParam String scenario, 
                                          @RequestParam(defaultValue = "327245033574678528") String appId) {
        try {
            VueProjectScenarioEnum scenarioEnum = VueProjectScenarioEnum.getEnumByValue(scenario);
            Long id = Long.parseLong(appId);
            
            // 获取AI服务（这会触发工具集创建）
            var aiService = vueProjectAiServiceFactory.getVueProjectAiService(id, scenarioEnum);
            
            log.info("场景: {} - 工具集已配置", scenarioEnum.getText());
            
            return String.format("工具集配置成功！\n场景: %s - %s\n服务: %s", 
                scenarioEnum.getValue(), 
                scenarioEnum.getText(),
                aiService.getClass().getSimpleName());
                
        } catch (Exception e) {
            log.error("工具集配置测试失败", e);
            return "测试失败: " + e.getMessage();
        }
    }

    /**
     * 测试Vue项目工具详情
     */
    @GetMapping("/vue-tools-detail")
    public String testVueToolsDetail(@RequestParam String scenario, 
                                   @RequestParam(defaultValue = "327245033574678528") String appId) {
        try {
            VueProjectScenarioEnum scenarioEnum = VueProjectScenarioEnum.getEnumByValue(scenario);
            Long id = Long.parseLong(appId);
            
            // 获取工具列表进行详细检查
            var tools = vueProjectAiServiceFactory.getToolsForScenario(scenarioEnum, id);
            
            StringBuilder result = new StringBuilder();
            result.append("工具详情检查\n");
            result.append("场景: ").append(scenarioEnum.getValue()).append(" - ").append(scenarioEnum.getText()).append("\n");
            result.append("工具数量: ").append(tools.size()).append("\n");
            result.append("工具列表:\n");
            
            for (int i = 0; i < tools.size(); i++) {
                Object tool = tools.get(i);
                String toolName = tool.getClass().getSimpleName();
                result.append("  ").append(i + 1).append(". ").append(toolName).append("\n");
            }
            
            log.info("场景: {} - 工具数量: {}, 工具: {}", 
                scenarioEnum.getText(), 
                tools.size(),
                tools.stream().map(tool -> tool.getClass().getSimpleName()).toList());
            
            return result.toString();
                
        } catch (Exception e) {
            log.error("工具详情检查失败", e);
            return "测试失败: " + e.getMessage();
        }
    }

    /**
     * 测试Vue项目场景检测
     */
    @GetMapping("/vue-scenario")
    public String testVueScenarioDetection(@RequestParam String message, 
                                         @RequestParam(defaultValue = "327245033574678528") String projectKey) {
        try {
            // 模拟appId
            Long appId = Long.parseLong(projectKey);
            
            // 测试场景检测
            VueProjectScenarioEnum scenario = vueProjectScenarioDetector.detectScenario(appId, message);
            
            log.info("Vue项目场景检测结果: {} - {}", scenario.getValue(), scenario.getText());
            log.info("用户消息: {}", message);
            log.info("项目Key: {}", projectKey);
            
            // 测试服务工厂
            try {
                var aiService = vueProjectAiServiceFactory.getVueProjectAiService(appId, scenario);
                log.info("成功获取Vue项目AI服务: {}", aiService.getClass().getSimpleName());
                
                return String.format("场景检测成功！\n场景: %s - %s\n服务类型: %s", 
                    scenario.getValue(), 
                    scenario.getText(),
                    aiService.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("获取Vue项目AI服务失败", e);
                return String.format("场景检测成功，但服务获取失败！\n场景: %s - %s\n错误: %s", 
                    scenario.getValue(), 
                    scenario.getText(),
                    e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Vue项目场景检测失败", e);
            return "测试失败: " + e.getMessage();
        }
    }
}