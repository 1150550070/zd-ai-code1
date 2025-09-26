package com.sht.zdaicode.config;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

public class SimpleRedisTest {

    @Test
    public void testDirectRedisConnection() {
        Jedis jedis = null;
        try {
            // 直接使用Jedis连接Redis
            jedis = new Jedis("192.168.100.128", 6379);
            jedis.auth("20040223tian");
            
            // 测试连接
            String pong = jedis.ping();
            System.out.println("Redis连接测试成功: " + pong);
            
            // 测试基本操作
            jedis.set("test:key", "test:value");
            String value = jedis.get("test:key");
            System.out.println("获取到值: " + value);
            
            // 清理
            jedis.del("test:key");
            
        } catch (Exception e) {
            System.err.println("Redis连接失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}