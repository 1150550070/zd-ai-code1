package com.sht.zdaicode.ai.tools.snapshot;

import java.time.LocalDateTime;

/**
 * 修改快照
 * 记录文件修改的历史信息
 */
public class ModifySnapshot {
    
    private String originalContent;
    private String oldContent;
    private String newContent;
    private String description;
    private LocalDateTime timestamp;

    public ModifySnapshot(String originalContent, String oldContent, String newContent, String description) {
        this.originalContent = originalContent;
        this.oldContent = oldContent;
        this.newContent = newContent;
        this.description = description;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getOriginalContent() {
        return originalContent;
    }

    public void setOriginalContent(String originalContent) {
        this.originalContent = originalContent;
    }

    public String getOldContent() {
        return oldContent;
    }

    public void setOldContent(String oldContent) {
        this.oldContent = oldContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("ModifySnapshot{description='%s', timestamp=%s}", description, timestamp);
    }
}