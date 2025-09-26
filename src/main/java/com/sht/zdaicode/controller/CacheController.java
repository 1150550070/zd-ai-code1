package com.sht.zdaicode.controller;

import com.sht.zdaicode.annotation.AuthCheck;
import com.sht.zdaicode.common.BaseResponse;
import com.sht.zdaicode.common.ResultUtils;
import com.sht.zdaicode.constant.UserConstant;
import com.sht.zdaicode.service.CacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 缓存管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/cache")
public class CacheController {

    @Resource
    private CacheService cacheService;

    /**
     * 清除精选应用缓存
     *
     * @return 操作结果
     */
    @PostMapping("/admin/evict/good-app")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> evictGoodAppCache() {
        try {
            cacheService.evictGoodAppPageCache();
            log.info("管理员手动清除精选应用缓存成功");
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("清除精选应用缓存失败", e);
            return ResultUtils.success(false);
        }
    }

    /**
     * 预热精选应用缓存
     *
     * @return 操作结果
     */
    @PostMapping("/admin/warmup/good-app")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> warmUpGoodAppCache() {
        try {
            // 异步执行预热，避免阻塞请求
            new Thread(() -> {
                try {
                    cacheService.warmUpGoodAppCache();
                    log.info("管理员手动预热精选应用缓存成功");
                } catch (Exception e) {
                    log.error("预热精选应用缓存失败", e);
                }
            }).start();
            
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("启动预热精选应用缓存失败", e);
            return ResultUtils.success(false);
        }
    }

    /**
     * 刷新精选应用缓存（清除 + 预热）
     *
     * @return 操作结果
     */
    @PostMapping("/admin/refresh/good-app")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> refreshGoodAppCache() {
        try {
            // 先清除缓存
            cacheService.evictGoodAppPageCache();
            
            // 异步预热缓存
            new Thread(() -> {
                try {
                    Thread.sleep(100); // 稍等一下确保清除完成
                    cacheService.warmUpGoodAppCache();
                    log.info("管理员手动刷新精选应用缓存成功");
                } catch (Exception e) {
                    log.error("预热精选应用缓存失败", e);
                }
            }).start();
            
            log.info("管理员手动刷新精选应用缓存操作已启动");
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("刷新精选应用缓存失败", e);
            return ResultUtils.success(false);
        }
    }
}