package com.sht.zdaicode.ai.tools.snapshot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修改快照管理器
 * 管理文件修改的历史快照
 */
@Slf4j
@Component
public class ModifySnapshotManager {

    /**
     * 存储快照的映射：appId -> filePath -> 快照列表
     */
    private final Map<Long, Map<String, List<ModifySnapshot>>> snapshotMap = new ConcurrentHashMap<>();

    /**
     * 每个文件最大保存的快照数量
     */
    private static final int MAX_SNAPSHOTS_PER_FILE = 10;

    /**
     * 保存修改快照
     *
     * @param appId 应用ID
     * @param filePath 文件路径
     * @param snapshot 快照
     */
    public void saveSnapshot(Long appId, String filePath, ModifySnapshot snapshot) {
        snapshotMap.computeIfAbsent(appId, k -> new ConcurrentHashMap<>())
                  .computeIfAbsent(filePath, k -> new ArrayList<>())
                  .add(snapshot);
        
        // 限制快照数量，移除最旧的快照
        List<ModifySnapshot> snapshots = snapshotMap.get(appId).get(filePath);
        if (snapshots.size() > MAX_SNAPSHOTS_PER_FILE) {
            snapshots.remove(0);
            log.debug("移除最旧的快照，文件: {}, 当前快照数: {}", filePath, snapshots.size());
        }
        
        log.info("保存修改快照 - appId: {}, 文件: {}, 描述: {}", appId, filePath, snapshot.getDescription());
    }

    /**
     * 获取最近的快照
     *
     * @param appId 应用ID
     * @param filePath 文件路径
     * @return 最近的快照，如果没有则返回null
     */
    public ModifySnapshot getLastSnapshot(Long appId, String filePath) {
        Map<String, List<ModifySnapshot>> fileSnapshots = snapshotMap.get(appId);
        if (fileSnapshots == null) {
            return null;
        }
        
        List<ModifySnapshot> snapshots = fileSnapshots.get(filePath);
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        
        return snapshots.get(snapshots.size() - 1);
    }

    /**
     * 移除最近的快照
     *
     * @param appId 应用ID
     * @param filePath 文件路径
     * @return 是否成功移除
     */
    public boolean removeLastSnapshot(Long appId, String filePath) {
        Map<String, List<ModifySnapshot>> fileSnapshots = snapshotMap.get(appId);
        if (fileSnapshots == null) {
            return false;
        }
        
        List<ModifySnapshot> snapshots = fileSnapshots.get(filePath);
        if (snapshots == null || snapshots.isEmpty()) {
            return false;
        }
        
        ModifySnapshot removed = snapshots.remove(snapshots.size() - 1);
        log.info("移除最近的快照 - appId: {}, 文件: {}, 描述: {}", appId, filePath, removed.getDescription());
        
        return true;
    }

    /**
     * 获取文件的修改历史
     *
     * @param appId 应用ID
     * @param filePath 文件路径
     * @return 修改历史列表
     */
    public List<ModifySnapshot> getModifyHistory(Long appId, String filePath) {
        Map<String, List<ModifySnapshot>> fileSnapshots = snapshotMap.get(appId);
        if (fileSnapshots == null) {
            return Collections.emptyList();
        }
        
        List<ModifySnapshot> snapshots = fileSnapshots.get(filePath);
        if (snapshots == null) {
            return Collections.emptyList();
        }
        
        // 返回副本，避免外部修改
        return new ArrayList<>(snapshots);
    }

    /**
     * 清理应用的所有快照
     *
     * @param appId 应用ID
     */
    public void clearAppSnapshots(Long appId) {
        Map<String, List<ModifySnapshot>> removed = snapshotMap.remove(appId);
        if (removed != null) {
            int totalSnapshots = removed.values().stream().mapToInt(List::size).sum();
            log.info("清理应用快照 - appId: {}, 清理文件数: {}, 清理快照数: {}", 
                    appId, removed.size(), totalSnapshots);
        }
    }

    /**
     * 清理文件的所有快照
     *
     * @param appId 应用ID
     * @param filePath 文件路径
     */
    public void clearFileSnapshots(Long appId, String filePath) {
        Map<String, List<ModifySnapshot>> fileSnapshots = snapshotMap.get(appId);
        if (fileSnapshots != null) {
            List<ModifySnapshot> removed = fileSnapshots.remove(filePath);
            if (removed != null) {
                log.info("清理文件快照 - appId: {}, 文件: {}, 清理快照数: {}", 
                        appId, filePath, removed.size());
            }
        }
    }

    /**
     * 获取快照统计信息
     *
     * @param appId 应用ID
     * @return 统计信息
     */
    public SnapshotStatistics getStatistics(Long appId) {
        Map<String, List<ModifySnapshot>> fileSnapshots = snapshotMap.get(appId);
        if (fileSnapshots == null) {
            return new SnapshotStatistics(0, 0);
        }
        
        int fileCount = fileSnapshots.size();
        int totalSnapshots = fileSnapshots.values().stream().mapToInt(List::size).sum();
        
        return new SnapshotStatistics(fileCount, totalSnapshots);
    }

    /**
     * 快照统计信息
     */
    public static class SnapshotStatistics {
        private final int fileCount;
        private final int totalSnapshots;

        public SnapshotStatistics(int fileCount, int totalSnapshots) {
            this.fileCount = fileCount;
            this.totalSnapshots = totalSnapshots;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getTotalSnapshots() {
            return totalSnapshots;
        }

        @Override
        public String toString() {
            return String.format("SnapshotStatistics{fileCount=%d, totalSnapshots=%d}", fileCount, totalSnapshots);
        }
    }
}