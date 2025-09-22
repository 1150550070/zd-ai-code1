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
        // Vue项目需要访问dist目录下的文件
        return serveVueProjectResource(appKey, request, AppConstant.CODE_OUTPUT_ROOT_DIR);
    }

    /**
     * Vue项目静态资源服务方法（处理dist目录）
     */
    private ResponseEntity<Resource> serveVueProjectResource(
            String resourceKey,
            HttpServletRequest request,
            String rootDir) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            
            // 根据请求路径确定前缀
            String prefix = "/static/";
            if (resourcePath.contains("/preview/")) {
                prefix = "/static/preview/";
            }
            
            resourcePath = resourcePath.substring((prefix + resourceKey).length());
            
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            
            // 默认返回 index.html - 处理目录访问
            if (resourcePath.equals("/") || resourcePath.equals("/dist/")) {
                resourcePath = resourcePath.equals("/") ? "/index.html" : "/dist/index.html";
            }
            
            // 直接使用项目根目录，不自动添加dist（因为URL中可能已经包含了dist路径）
            Path basePath = Paths.get(rootDir, resourceKey);
            String cleanResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            Path fullPath = basePath.resolve(cleanResourcePath);
            File file = fullPath.toFile();
            String filePath = fullPath.toString();
            
            log.info("Vue项目预览访问: resourceKey={}, resourcePath={}, filePath={}", resourceKey, resourcePath, filePath);
            
            // 检查文件是否存在
            if (!file.exists()) {
                log.warn("Vue项目文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            // 检查文件是否可读（避免访问被锁定的文件）
            if (!file.canRead()) {
                log.warn("Vue项目文件被锁定或无法读取: {}", filePath);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "5")
                        .build();
            }
            
            // 返回文件资源
            Resource resource;
            try {
                resource = new FileSystemResource(file);
                // 尝试获取输入流以验证文件可访问性
                resource.getInputStream().close();
            } catch (Exception e) {
                log.warn("Vue项目文件访问异常: {}, 错误: {}", filePath, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "5")
                        .build();
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", getContentTypeWithCharset(filePath));
            
            // 添加CORS头部支持跨域访问
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "*");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            log.error("Vue项目预览服务异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 通用静态资源服务方法
     */
    private ResponseEntity<Resource> serveStaticResource(
            String resourceKey,
            HttpServletRequest request,
            String rootDir) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            
            // 根据请求路径确定前缀
            String prefix = "/static/";
            if (resourcePath.contains("/preview/")) {
                prefix = "/static/preview/";
            }
            
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
            
            // 构建文件路径 - 使用Path.resolve避免路径分隔符问题
            Path basePath = Paths.get(rootDir, resourceKey);
            // 处理resourcePath，移除开头的斜杠（如果存在）
            String cleanResourcePath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            Path fullPath = basePath.resolve(cleanResourcePath);
            File file = fullPath.toFile();
            String filePath = fullPath.toString();
            
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            // 检查文件是否可读（避免访问被锁定的文件）
            if (!file.canRead()) {
                log.warn("文件被锁定或无法读取: {}", filePath);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "5")
                        .build();
            }
            
            // 返回文件资源
            Resource resource;
            try {
                resource = new FileSystemResource(file);
                // 尝试获取输入流以验证文件可访问性
                resource.getInputStream().close();
            } catch (Exception e) {
                log.warn("文件访问异常: {}, 错误: {}", filePath, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "5")
                        .build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", getContentTypeWithCharset(filePath));
            
            // 添加CORS头部支持跨域访问
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.add("Access-Control-Allow-Headers", "*");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
        return "application/octet-stream";
    }
}
