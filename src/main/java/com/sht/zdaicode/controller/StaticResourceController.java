package com.sht.zdaicode.controller;

import com.sht.zdaicode.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    /**
     * 提供部署后的静态资源访问
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveDeployedResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        return serveStaticResource(deployKey, request, AppConstant.CODE_DEPLOY_ROOT_DIR);
    }

    /**
     * 提供预览静态资源访问（用于应用生成后的预览）
     * 访问格式：http://localhost:8123/api/static/preview/{appKey}[/{fileName}]
     */
    @GetMapping("/preview/{appKey}/**")
    public ResponseEntity<Resource> servePreviewResource(
            @PathVariable String appKey,
            HttpServletRequest request) {

        // --- 核心修复逻辑开始 ---
        // 根据 appKey 前缀判断项目类型，路由到不同的资源目录

        // 1. 全栈项目 (key 通常以 fullstack_ 开头)：前端构建产物在 frontend/dist 目录下
        if (appKey != null && appKey.toLowerCase().startsWith("fullstack_")) {
            return serveFullStackProjectResource(appKey, request, AppConstant.CODE_OUTPUT_ROOT_DIR);
        }

        // 2. Vue 项目 (key 通常以 vue_ 开头)：构建产物在 dist 目录下
        if (appKey != null && appKey.toLowerCase().startsWith("vue_")) {
            return serveVueProjectResource(appKey, request, AppConstant.CODE_OUTPUT_ROOT_DIR);
        }

        // 3. HTML / 多文件项目 (key 通常以 html_ 或 multi_ 开头)：资源直接在根目录下
        return serveStaticResource(appKey, request, AppConstant.CODE_OUTPUT_ROOT_DIR);
        // --- 核心修复逻辑结束 ---
    }

    /**
     * 全栈项目静态资源服务方法（处理 frontend/dist 目录）
     */
    private ResponseEntity<Resource> serveFullStackProjectResource(
            String resourceKey,
            HttpServletRequest request,
            String rootDir) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            // 根据请求路径确定前缀
            String prefix = resourcePath.contains("/preview/") ? "/static/preview/" : "/static/";
            resourcePath = resourcePath.substring((prefix + resourceKey).length());

            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }

            // 默认返回 index.html
            if (resourcePath.equals("/") || resourcePath.equals("/frontend/dist/")) {
                resourcePath = "/index.html";
            }

            // ================= 核心修复逻辑开始 =================
            // 解决请求 URL Key 与物理文件夹名称不一致的问题
            // 1. 从 resourceKey (如 FULLSTACK_421413026411491328) 中提取出末尾的 ID
            String appId = resourceKey.substring(resourceKey.lastIndexOf("_") + 1);

            // 2. 映射到真实的物理目录 (拼装成 fullstack_app_421413026411491328)
            String realDirName = "fullstack_app_" + appId;

            // 3. 将 basePath 指向正确的真实物理目录下的 frontend/dist
            Path basePath = Paths.get(rootDir, realDirName, "frontend", "dist");
            // ================= 核心修复逻辑结束 =================

            String cleanResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            Path fullPath = basePath.resolve(cleanResourcePath);
            File file = fullPath.toFile();
            String filePath = fullPath.toString();

            log.info("全栈项目预览访问: 请求Key={}, 映射物理目录={}, 最终路径={}", resourceKey, realDirName, filePath);

            if (!file.exists()) {
                log.warn("全栈项目文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            if (!file.canRead()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            Resource resource = new FileSystemResource(file);
            return createResourceResponse(resource, filePath);

        } catch (Exception e) {
            log.error("全栈项目预览服务异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Vue项目静态资源服务方法（处理 dist 目录）
     */
    private ResponseEntity<Resource> serveVueProjectResource(
            String resourceKey,
            HttpServletRequest request,
            String rootDir) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            // 根据请求路径确定前缀
            String prefix = resourcePath.contains("/preview/") ? "/static/preview/" : "/static/";
            resourcePath = resourcePath.substring((prefix + resourceKey).length());

            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }

            // 默认返回 index.html
            if (resourcePath.equals("/") || resourcePath.equals("/dist/")) {
                resourcePath = "/index.html"; // 修正了原版可能导致 /dist/dist/index.html 的小隐患
            }

            // 强制将 basePath 指向 dist 目录
            Path basePath = Paths.get(rootDir, resourceKey, "dist");

            String cleanResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            Path fullPath = basePath.resolve(cleanResourcePath);
            File file = fullPath.toFile();
            String filePath = fullPath.toString();

            log.info("Vue项目预览访问: resourceKey={}, filePath={}", resourceKey, filePath);

            if (!file.exists()) {
                log.warn("Vue项目文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            if (!file.canRead()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            Resource resource = new FileSystemResource(file);
            return createResourceResponse(resource, filePath);

        } catch (Exception e) {
            log.error("Vue项目预览服务异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 通用静态资源服务方法 (HTML/多文件/部署后应用)
     */
    private ResponseEntity<Resource> serveStaticResource(
            String resourceKey,
            HttpServletRequest request,
            String rootDir) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

            // 根据请求路径确定前缀
            String prefix = resourcePath.contains("/preview/") ? "/static/preview/" : "/static/";
            resourcePath = resourcePath.substring((prefix + resourceKey).length());

            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }

            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }

            // 直接使用根目录
            Path basePath = Paths.get(rootDir, resourceKey);
            String cleanResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            Path fullPath = basePath.resolve(cleanResourcePath);
            File file = fullPath.toFile();
            String filePath = fullPath.toString();

            log.info("静态资源访问: resourceKey={}, filePath={}", resourceKey, filePath);

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            if (!file.canRead()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

            Resource resource = new FileSystemResource(file);
            return createResourceResponse(resource, filePath);

        } catch (Exception e) {
            log.error("静态资源服务异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 统一构建响应对象
     */
    private ResponseEntity<Resource> createResourceResponse(Resource resource, String filePath) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", getContentTypeWithCharset(filePath));
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "*");

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        if (filePath.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}