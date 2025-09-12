package com.sht.zdaicode.langgraph4j.tools;

import com.sht.zdaicode.ZdAiCodeApplication;
import com.sht.zdaicode.langgraph4j.model.ImageResource;
import com.sht.zdaicode.langgraph4j.model.enums.ImageCategoryEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基于Pixabay API的插画工具测试类
 */
@SpringBootTest(classes = ZdAiCodeApplication.class)
class UndrawIllustrationToolTest {

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Test
    void testSearchIllustrations() {
        // 测试搜索插画功能
        List<ImageResource> illustrations = undrawIllustrationTool.searchIllustrations("technology");
        assertNotNull(illustrations, "插画列表不应为null");
        
        System.out.println("=== 插画搜索测试 ===");
        System.out.println("搜索关键词: technology");
        System.out.println("搜索到 " + illustrations.size() + " 张插画");
        
        // 如果有结果，验证返回的插画资源
        if (!illustrations.isEmpty()) {
            // 验证第一张插画的基本属性
            ImageResource firstIllustration = illustrations.get(0);
            assertEquals(ImageCategoryEnum.ILLUSTRATION, firstIllustration.getCategory(), "图片类别应为插画");
            assertNotNull(firstIllustration.getDescription(), "插画描述不应为空");
            assertNotNull(firstIllustration.getUrl(), "插画URL不应为空");
            assertTrue(firstIllustration.getUrl().startsWith("http"), "插画URL应以http开头");
            
            // 打印所有插画信息
            illustrations.forEach(illustration ->
                    System.out.println("插画: " + illustration.getDescription() + " - " + illustration.getUrl())
            );
            
            // 验证数量合理性
            assertTrue(illustrations.size() <= 12, "插画数量不应超过12张");
            
        } else {
            System.out.println("未获取到插画资源，可能是网络问题或API限制");
        }
        
        System.out.println("插画搜索测试完成");
    }

    @Test
    void testSearchVectorIcons() {
        // 测试搜索矢量图和图标功能
        List<ImageResource> vectors = undrawIllustrationTool.searchVectorIcons("business");
        assertNotNull(vectors, "矢量图列表不应为null");
        
        System.out.println("=== 矢量图搜索测试 ===");
        System.out.println("搜索关键词: business");
        System.out.println("搜索到 " + vectors.size() + " 张矢量图");
        
        if (!vectors.isEmpty()) {
            // 验证第一张矢量图的基本属性
            ImageResource firstVector = vectors.get(0);
            assertEquals(ImageCategoryEnum.ILLUSTRATION, firstVector.getCategory(), "图片类别应为插画");
            assertNotNull(firstVector.getDescription(), "矢量图描述不应为空");
            assertNotNull(firstVector.getUrl(), "矢量图URL不应为空");
            assertTrue(firstVector.getUrl().startsWith("http"), "矢量图URL应以http开头");
            assertTrue(firstVector.getDescription().contains("矢量图"), "描述应包含'矢量图'标识");
            
            // 打印所有矢量图信息
            vectors.forEach(vector ->
                    System.out.println("矢量图: " + vector.getDescription() + " - " + vector.getUrl())
            );
            
            // 验证数量合理性
            assertTrue(vectors.size() <= 8, "矢量图数量不应超过8张");
            
        } else {
            System.out.println("未获取到矢量图资源，可能是网络问题或API限制");
        }
        
        System.out.println("矢量图搜索测试完成");
    }

    @Test
    void testSearchWithDifferentKeywords() {
        // 测试不同关键词的搜索效果
        String[] keywords = {"nature", "education", "health", "travel"};
        
        System.out.println("=== 多关键词搜索测试 ===");
        
        for (String keyword : keywords) {
            List<ImageResource> results = undrawIllustrationTool.searchIllustrations(keyword);
            assertNotNull(results, "搜索结果不应为null，关键词: " + keyword);
            
            System.out.println("关键词 '" + keyword + "' 搜索到 " + results.size() + " 张插画");
            
            // 如果有结果，验证基本属性
            if (!results.isEmpty()) {
                ImageResource sample = results.get(0);
                assertEquals(ImageCategoryEnum.ILLUSTRATION, sample.getCategory());
                assertNotNull(sample.getDescription());
                assertNotNull(sample.getUrl());
            }
        }
        
        System.out.println("多关键词搜索测试完成");
    }

    @Test
    void testEmptyKeywordHandling() {
        // 测试空关键词处理
        System.out.println("=== 空关键词处理测试 ===");
        
        List<ImageResource> emptyResults = undrawIllustrationTool.searchIllustrations("");
        assertNotNull(emptyResults, "空关键词搜索结果不应为null");
        System.out.println("空关键词搜索结果数量: " + emptyResults.size());
        
        System.out.println("空关键词处理测试完成");
    }
}