package com.sht.zdaicode.service.impl;

import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.model.dto.app.AppQueryRequest;
import com.sht.zdaicode.service.AppService;
import com.sht.zdaicode.service.CacheService;
import com.sht.zdaicode.utils.CacheKeyUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 缓存管理服务实现类
 */
@Slf4j
@Service
public class CacheServiceImpl implements CacheService {
    
    @Resource
    private CacheManager cacheManager;
    
    @Resource
    private AppService appService;
    
    private static final String GOOD_APP_CACHE_NAME = "good_app_page";
    
    @Override
    public void evictGoodAppPageCache() {
        try {
            // 清除整个精选应用缓存
            Objects.requireNonNull(cacheManager.getCache(GOOD_APP_CACHE_NAME)).clear();
            log.info("已清除所有精选应用页面缓存");
        } catch (Exception e) {
            log.error("清除精选应用页面缓存失败", e);
        }
    }
    
    @Override
    public void evictGoodAppPageCache(AppQueryRequest appQueryRequest) {
        try {
            String cacheKey = CacheKeyUtils.generateKey(appQueryRequest);
            Objects.requireNonNull(cacheManager.getCache(GOOD_APP_CACHE_NAME)).evict(cacheKey);
            log.info("已清除指定查询条件的精选应用缓存，key: {}", cacheKey);
        } catch (Exception e) {
            log.error("清除指定精选应用缓存失败", e);
        }
    }
    
    @Override
    public void warmUpGoodAppCache() {
        try {
            log.info("开始预热精选应用缓存");
            
            // 预热前3页的数据，每页20条
            for (int pageNum = 1; pageNum <= 3; pageNum++) {
                AppQueryRequest request = new AppQueryRequest();
                request.setPageNum(pageNum);
                request.setPageSize(20);
                request.setPriority(AppConstant.GOOD_APP_PRIORITY);
                
                // 触发缓存加载
                try {
                    appService.listGoodAppVOByPage(request);
                    log.debug("预热精选应用缓存第{}页完成", pageNum);
                } catch (Exception e) {
                    log.warn("预热精选应用缓存第{}页失败: {}", pageNum, e.getMessage());
                }
            }
            
            log.info("精选应用缓存预热完成");
        } catch (Exception e) {
            log.error("精选应用缓存预热失败", e);
        }
    }
}