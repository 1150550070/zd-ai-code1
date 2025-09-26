package com.sht.zdaicode.service;

/**
 * 截图服务接口
 *
 * @author yupi
 */
public interface ScreenshotService {

    /**
     * 生成网页截图并上传到对象存储
     *
     * @param webUrl 网页URL
     * @return 对象存储访问URL
     */
    String generateAndUploadScreenshot(String webUrl);
}