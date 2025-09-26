package com.sht.zdaicode.controller;

import com.sht.zdaicode.common.BaseResponse;
import com.sht.zdaicode.common.ResultUtils;
import com.sht.zdaicode.manager.OssManager;
import com.sht.zdaicode.config.OssClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * OSS调试控制器
 */
@RestController
@RequestMapping("/oss/debug")
@Slf4j
public class OssDebugController {

    @Autowired
    private OssClientConfig ossClientConfig;

    /**
     * 获取OSS配置信息
     */
    @GetMapping("/config")
    public BaseResponse<Map<String, Object>> getOssConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", ossClientConfig.getHost());
        config.put("bucket", ossClientConfig.getBucket());
        config.put("region", ossClientConfig.getRegion());
        
        // 生成示例URL
        String sampleKey = "screenshots/2025/09/10/sample.jpg";
        String sampleUrl = String.format("https://%s.%s/%s", 
            ossClientConfig.getBucket(), 
            ossClientConfig.getHost(), 
            sampleKey);
        config.put("sampleUrl", sampleUrl);
        
        return ResultUtils.success(config);
    }

    /**
     * 测试URL格式生成
     */
    @GetMapping("/test-url")
    public BaseResponse<Map<String, String>> testUrlGeneration(@RequestParam String key) {
        Map<String, String> result = new HashMap<>();
        
        // 清理key
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        
        // 生成不同格式的URL进行测试
        String standardUrl = String.format("https://%s.%s/%s", 
            ossClientConfig.getBucket(), 
            ossClientConfig.getHost(), 
            cleanKey);
            
        String alternativeUrl = String.format("https://%s/%s", 
            ossClientConfig.getHost(), 
            cleanKey);
            
        result.put("standardUrl", standardUrl);
        result.put("alternativeUrl", alternativeUrl);
        result.put("cleanKey", cleanKey);
        result.put("originalKey", key);
        
        return ResultUtils.success(result);
    }
}