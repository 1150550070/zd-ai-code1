package com.sht.zdaicode.core.preview;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PreviewProcessManager {

    private final DataSource dataSource;
    
    // 存储 appId -> Process
    private final Map<Long, Process> processMap = new ConcurrentHashMap<>();
    // 存储 appId -> 运行时分配的端口
    private final Map<Long, Integer> portMap = new ConcurrentHashMap<>();

    public PreviewProcessManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 初始化数据库并执行 SQL 脚本
     */
    public void initDatabase(Long appId, String sqlFilePath) throws Exception {
        String dbName = "fullstack_app_" + appId;
        log.info("开始为应用 {} 初始化数据库: {}", appId, dbName);
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
             
            // 1. 创建数据库
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + dbName + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("USE " + dbName);
            
            // 2. 如果存在 SQL 脚本，则执行
            if (FileUtil.exist(sqlFilePath)) {
                try {
                    ScriptUtils.executeSqlScript(conn, new FileSystemResource(sqlFilePath));
                    log.info("成功执行数据库脚本: {}", sqlFilePath);
                } catch (Exception e) {
                    log.warn("执行数据库脚本遇到错误，可能表已存在: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * 启动后端的 jar 包并获取绑定的端口
     */
    public int startBackendProcess(Long appId, String jarPath) throws Exception {
        // 先停掉旧进程
        stopBackendProcess(appId);
        
        int targetPort = 10000 + (int) (appId % 40000);
        String dbName = "fullstack_app_" + appId;
        String jdbcUrl = "jdbc:mysql://localhost:3306/" + dbName + "?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        
        String[] command = {
                "java",
                "-jar",
                jarPath,
                "--server.port=" + targetPort,
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=root",
                "--spring.datasource.password=20040223tian" // 使用本地 MySQL 的实际密码
        };
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true); // 将错误流合并到标准输出
        Process process = pb.start();
        
        processMap.put(appId, process);
        portMap.put(appId, targetPort);
        
        log.info("应用 {} 的后台进程启动成功，绑定固定端口: {}", appId, targetPort);
        
        // 启动后台线程继续消费日志，防止缓冲区满导致进程阻塞
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String logLine;
                while ((logLine = reader.readLine()) != null) {
                    log.debug("[App-{}-Backend] {}", appId, logLine);
                }
            } catch (Exception e) {
                // ignore
            }
        });
        
        // 等待几秒钟让进程启动完毕 (最简单的等待策略)
        Thread.sleep(3000);
        
        return targetPort;
    }
    
    /**
     * 停止后端进程
     */
    public void stopBackendProcess(Long appId) {
        Process process = processMap.remove(appId);
        portMap.remove(appId);
        if (process != null) {
            process.destroyForcibly();
            log.info("已停止应用 {} 的后台进程", appId);
        }
    }
    
    public Integer getBackendPort(Long appId) {
        return portMap.get(appId);
    }
}
