package com.sht.zdaicode.controller;

import com.sht.zdaicode.common.BaseResponse;
import com.sht.zdaicode.common.ResultUtils;
import com.sht.zdaicode.service.ScreenshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * OSS测试控制器
 *
 * @author yupi
 */
@RestController
@RequestMapping("/oss/test")
@Slf4j
@Tag(name = "OSS测试", description = "OSS功能测试接口")
public class OssTestController {

    @Autowired
    private ScreenshotService screenshotService;

    /**
     * 测试截图上传功能
     */
    @PostMapping("/screenshot")
    @Operation(summary = "测试截图上传", description = "测试网页截图生成并上传到OSS")
    public BaseResponse<?> testScreenshot(@RequestParam String url) {
        try {
            log.info("开始测试截图上传功能，URL: {}", url);
            String ossUrl = screenshotService.generateAndUploadScreenshot(url);
            if (ossUrl != null) {
                return ResultUtils.success(ossUrl);
            } else {
                return ResultUtils.error(500, "截图上传失败");
            }
        } catch (Exception e) {
            log.error("测试截图上传失败: {}", url, e);
            return ResultUtils.error(500, "测试失败: " + e.getMessage());
        }
    }
}