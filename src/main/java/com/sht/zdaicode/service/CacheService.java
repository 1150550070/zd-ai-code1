package com.sht.zdaicode.service;

import com.sht.zdaicode.model.dto.app.AppQueryRequest;

/**
 * 缓存管理服务接口
 */
public interface CacheService {
    
    /**
     * 清除精选应用页面缓存
     */
    void evictGoodAppPageCache();
    
    /**
     * 清除指定查询条件的精选应用缓存
     * 
     * @param appQueryRequest 查询条件
     */
    void evictGoodAppPageCache(AppQueryRequest appQueryRequest);
    
    /**
     * 预热精选应用缓存
     * 预加载前几页的精选应用数据到缓存中
     */
    void warmUpGoodAppCache();
}