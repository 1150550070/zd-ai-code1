package com.sht.zdaicode.controller;

import com.sht.zdaicode.ai.SmartToolSelector;
import com.sht.zdaicode.ai.tools.progress.ProgressNotifier;
import com.sht.zdaicode.ai.tools.snapshot.ModifySnapshotManager;
import com.sht.zdaicode.model.enums.VueProjectScenarioEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工具优化测试控制器
 * 用于测试智能工具选择器和用户体验优化功能
 */
@RestController
@RequestMapping("/api/tool-optimization")
@Slf4j
public class ToolOptimizationTestController {

    @Resource
    private SmartToolSelector smartToolSelector;
    
    @Resource
    private ProgressNotifier progressNotifier;
    
    @Resource
    private ModifySnapshotManager snapshotManager;

    /**
     * 测试智能工具选择
     */
    @PostMapping("/test-smart-tool-selection")
    public Map<String, Object> testSmartToolSelection(
            @RequestParam Long appId,
            @RequestParam String scenario,
            @RequestParam String userMessage) {
        
        log.info("测试智能工具选择 - appId: {}, scenario: {}, message: {}", appId, scenario, userMessage);
        
        try {
            VueProjectScenarioEnum scenarioEnum = VueProjectScenarioEnum.valueOf(scenario.toUpperCase());
            List<Object> tools = smartToolSelector.selectOptimalTools(scenarioEnum, appId, userMessage);
            
            return Map.of(
                "success", true,
                "appId", appId,
                "scenario", scenarioEnum.getText(),
                "userMessage", userMessage,
                "selectedTools", tools.stream().map(tool -> tool.getClass().getSimpleName()).toList(),
                "toolCount", tools.size()
            );
            
        } catch (Exception e) {
            log.error("智能工具选择测试失败", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 测试进度通知功能
     */
    @PostMapping("/test-progress-notification")
    public Map<String, Object> testProgressNotification() {
        log.info("测试进度通知功能");
        
        try {
            String operationId = "test-operation-" + System.currentTimeMillis();
            
            // 模拟操作进度
            Thread.startVirtualThread(() -> {
                try {
                    progressNotifier.notifyStart(operationId, "开始测试操作");
                    Thread.sleep(1000);
                    
                    progressNotifier.notifyProgress(operationId, 25, 100, "执行第一步");
                    Thread.sleep(1000);
                    
                    progressNotifier.notifyProgress(operationId, 50, 100, "执行第二步");
                    Thread.sleep(1000);
                    
                    progressNotifier.notifyProgress(operationId, 75, 100, "执行第三步");
                    Thread.sleep(1000);
                    
                    progressNotifier.notifyComplete(operationId, "测试操作完成");
                    
                } catch (InterruptedException e) {
                    progressNotifier.notifyError(operationId, "操作被中断");
                    Thread.currentThread().interrupt();
                }
            });
            
            return Map.of(
                "success", true,
                "operationId", operationId,
                "message", "进度通知测试已启动，请查看日志"
            );
            
        } catch (Exception e) {
            log.error("进度通知测试失败", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 获取进度信息
     */
    @GetMapping("/progress/{operationId}")
    public Map<String, Object> getProgress(@PathVariable String operationId) {
        ProgressNotifier.ProgressInfo progress = progressNotifier.getProgress(operationId);
        
        if (progress == null) {
            return Map.of(
                "success", false,
                "message", "未找到操作进度信息"
            );
        }
        
        return Map.of(
            "success", true,
            "operationId", progress.getOperationId(),
            "message", progress.getMessage(),
            "current", progress.getCurrent(),
            "total", progress.getTotal(),
            "percentage", progress.getProgressPercentage(),
            "completed", progress.isCompleted(),
            "error", progress.isError(),
            "formattedTime", progress.getFormattedTime()
        );
    }

    /**
     * 测试快照管理功能
     */
    @PostMapping("/test-snapshot-management")
    public Map<String, Object> testSnapshotManagement(@RequestParam Long appId) {
        log.info("测试快照管理功能 - appId: {}", appId);
        
        try {
            // 获取快照统计
            ModifySnapshotManager.SnapshotStatistics stats = snapshotManager.getStatistics(appId);
            
            return Map.of(
                "success", true,
                "appId", appId,
                "fileCount", stats.getFileCount(),
                "totalSnapshots", stats.getTotalSnapshots(),
                "statistics", stats.toString()
            );
            
        } catch (Exception e) {
            log.error("快照管理测试失败", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 清理测试数据
     */
    @DeleteMapping("/cleanup/{appId}")
    public Map<String, Object> cleanup(@PathVariable Long appId) {
        log.info("清理测试数据 - appId: {}", appId);
        
        try {
            snapshotManager.clearAppSnapshots(appId);
            
            return Map.of(
                "success", true,
                "message", "测试数据清理完成",
                "appId", appId
            );
            
        } catch (Exception e) {
            log.error("清理测试数据失败", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 获取工具优化功能状态
     */
    @GetMapping("/status")
    public Map<String, Object> getOptimizationStatus() {
        return Map.of(
            "smartToolSelector", "已启用",
            "progressNotifier", "已启用", 
            "snapshotManager", "已启用",
            "features", List.of(
                "智能工具选择",
                "进度实时反馈",
                "增量修改支持",
                "修改历史管理",
                "批量操作优化"
            )
        );
    }
}