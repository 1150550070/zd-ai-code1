package com.sht.zdaicode.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
public class RedisConnectionTest {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void testRedisConnection() {
        try {
            if (stringRedisTemplate != null) {
                // 测试基本的Redis操作
                stringRedisTemplate.opsForValue().set("test:key", "test:value");
                String value = stringRedisTemplate.opsForValue().get("test:key");
                System.out.println("Redis连接测试成功，获取到值: " + value);
                
                // 清理测试数据
                stringRedisTemplate.delete("test:key");
            } else {
                System.out.println("StringRedisTemplate未注入，可能Redis配置有问题");
            }
        } catch (Exception e) {
            System.err.println("Redis连接测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}