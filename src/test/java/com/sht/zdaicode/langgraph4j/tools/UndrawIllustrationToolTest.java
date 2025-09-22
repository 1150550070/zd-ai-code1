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
        // 测试正常搜索插画
        List<ImageResource> illustrations = undrawIllustrationTool.searchIllustrations("happy");
        assertNotNull(illustrations);
        
        // 验证返回的插画资源
        ImageResource firstIllustration = illustrations.get(0);
        assertEquals(ImageCategoryEnum.ILLUSTRATION, firstIllustration.getCategory());
        assertNotNull(firstIllustration.getDescription());
        assertNotNull(firstIllustration.getUrl());
        assertTrue(firstIllustration.getUrl().startsWith("http"));
        System.out.println("搜索到 " + illustrations.size() + " 张插画");
        illustrations.forEach(illustration -> 
            System.out.println("插画: " + illustration.getDescription() + " - " + illustration.getUrl())
        );
    }

    @Test
    void testSearchVectorIcons() {
        // 测试搜索矢量图和图标功能
        List<ImageResource> vectors = undrawIllustrationTool.searchVectorIcons("business");
        assertNotNull(vectors);
        
        // 验证返回的矢量图资源
        if (!vectors.isEmpty()) {
            ImageResource firstVector = vectors.get(0);
            assertEquals(ImageCategoryEnum.ILLUSTRATION, firstVector.getCategory());
            assertNotNull(firstVector.getDescription());
            assertNotNull(firstVector.getUrl());
            assertTrue(firstVector.getUrl().startsWith("http"));
            assertTrue(firstVector.getDescription().contains("矢量图"));
            System.out.println("搜索到 " + vectors.size() + " 张矢量图");
            vectors.forEach(vector ->
                System.out.println("矢量图: " + vector.getDescription() + " - " + vector.getUrl())
            );
        }
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