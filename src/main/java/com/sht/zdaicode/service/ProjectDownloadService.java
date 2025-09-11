package com.sht.zdaicode.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {
    /**
     * 下载项目为ZIP文件
     *
     * @param projectPath    项目路径
     * @param downLoadFileName 下载文件名
     * @param response       HTTP响应对象
     */
    void downloadProjectAsZip(String projectPath, String downLoadFileName, HttpServletResponse response);


}
