package com.sht.zdaicode.ai.tools.progress;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度通知器
 * 负责发送工具执行进度通知
 */
@Slf4j
@Component
public class ProgressNotifier {

    /**
     * 存储操作进度信息
     */
    private final Map<String, ProgressInfo> progressMap = new ConcurrentHashMap<>();

    /**
     * 通知操作开始
     *
     * @param operationId 操作ID
     * @param message 开始消息
     */
    public void notifyStart(String operationId, String message) {
        ProgressInfo progress = new ProgressInfo(operationId, message, 0, 100);
        progressMap.put(operationId, progress);
        
        log.info("操作开始 - ID: {}, 消息: {}", operationId, message);
        
        // 这里可以集成WebSocket或SSE来实时推送给前端
        sendProgressUpdate(progress);
    }

    /**
     * 通知操作进度
     *
     * @param operationId 操作ID
     * @param current 当前进度
     * @param total 总进度
     */
    public void notifyProgress(String operationId, int current, int total) {
        notifyProgress(operationId, current, total, null);
    }

    /**
     * 通知操作进度（带消息）
     *
     * @param operationId 操作ID
     * @param current 当前进度
     * @param total 总进度
     * @param message 进度消息
     */
    public void notifyProgress(String operationId, int current, int total, String message) {
        ProgressInfo progress = progressMap.get(operationId);
        if (progress != null) {
            progress.setCurrent(current);
            progress.setTotal(total);
            if (message != null) {
                progress.setMessage(message);
            }
            progress.setLastUpdate(LocalDateTime.now());
            
            log.debug("操作进度 - ID: {}, 进度: {}/{}, 消息: {}", operationId, current, total, message);
            
            sendProgressUpdate(progress);
        }
    }

    /**
     * 通知操作完成
     *
     * @param operationId 操作ID
     * @param message 完成消息
     */
    public void notifyComplete(String operationId, String message) {
        ProgressInfo progress = progressMap.get(operationId);
        if (progress != null) {
            progress.setCurrent(progress.getTotal());
            progress.setMessage(message);
            progress.setCompleted(true);
            progress.setLastUpdate(LocalDateTime.now());
            
            log.info("操作完成 - ID: {}, 消息: {}", operationId, message);
            
            sendProgressUpdate(progress);
            
            // 延迟清理进度信息
            cleanupProgressAfterDelay(operationId);
        }
    }

    /**
     * 通知操作错误
     *
     * @param operationId 操作ID
     * @param errorMessage 错误消息
     */
    public void notifyError(String operationId, String errorMessage) {
        ProgressInfo progress = progressMap.get(operationId);
        if (progress != null) {
            progress.setMessage(errorMessage);
            progress.setError(true);
            progress.setLastUpdate(LocalDateTime.now());
            
            log.error("操作错误 - ID: {}, 错误: {}", operationId, errorMessage);
            
            sendProgressUpdate(progress);
            
            // 延迟清理进度信息
            cleanupProgressAfterDelay(operationId);
        }
    }

    /**
     * 获取操作进度
     *
     * @param operationId 操作ID
     * @return 进度信息
     */
    public ProgressInfo getProgress(String operationId) {
        return progressMap.get(operationId);
    }

    /**
     * 发送进度更新（可以扩展为WebSocket或SSE推送）
     *
     * @param progress 进度信息
     */
    private void sendProgressUpdate(ProgressInfo progress) {
        // 目前只是日志输出，后续可以集成WebSocket或SSE
        String progressJson = JSONUtil.toJsonStr(progress);
        log.debug("进度更新: {}", progressJson);
        
        // TODO: 集成WebSocket或SSE推送
        // webSocketService.sendProgress(progress);
        // sseService.sendProgress(progress);
    }

    /**
     * 延迟清理进度信息
     *
     * @param operationId 操作ID
     */
    private void cleanupProgressAfterDelay(String operationId) {
        // 使用虚拟线程延迟清理
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(30000); // 30秒后清理
                progressMap.remove(operationId);
                log.debug("清理进度信息 - ID: {}", operationId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 进度信息内部类
     */
    public static class ProgressInfo {
        private String operationId;
        private String message;
        private int current;
        private int total;
        private boolean completed = false;
        private boolean error = false;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdate;

        public ProgressInfo(String operationId, String message, int current, int total) {
            this.operationId = operationId;
            this.message = message;
            this.current = current;
            this.total = total;
            this.startTime = LocalDateTime.now();
            this.lastUpdate = LocalDateTime.now();
        }

        // Getters and Setters
        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public boolean isError() {
            return error;
        }

        public void setError(boolean error) {
            this.error = error;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public LocalDateTime getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(LocalDateTime lastUpdate) {
            this.lastUpdate = lastUpdate;
        }

        public double getProgressPercentage() {
            return total > 0 ? (double) current / total * 100 : 0;
        }

        public String getFormattedTime() {
            return lastUpdate.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }
}