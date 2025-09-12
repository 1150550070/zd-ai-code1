package com.sht.zdaicode.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sht.zdaicode.langgraph4j.model.ImageResource;
import com.sht.zdaicode.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于Pixabay API的插画搜索工具
 * 提供高质量的插画、矢量图和图标资源
 */
@Slf4j
@Component
public class UndrawIllustrationTool {

    private static final String PIXABAY_API_URL = "https://pixabay.com/api/";

    @Value("${pixabay.api-key:44863301-5c4b6576c6b9c70c78c39c3c5}")
    private String pixabayApiKey;

    @Tool("搜索插画图片，用于网站美化和装饰，支持矢量图、插画和图标")
    public List<ImageResource> searchIllustrations(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        int searchCount = 12;

        // 使用 try-with-resources 自动释放 HTTP 资源
        try (HttpResponse response = HttpRequest.get(PIXABAY_API_URL)
                .form("key", pixabayApiKey)
                .form("q", query)
                .form("image_type", "illustration")  // 专门搜索插画
                .form("category", "backgrounds,fashion,nature,science,education,feelings,health,people,places,animals,industry,computer,food,sports,transportation,travel,buildings,business,music")
                .form("per_page", searchCount)
                .form("page", 1)
                .form("safesearch", "true")
                .form("order", "popular")  // 按受欢迎程度排序
                .timeout(15000)
                .execute()) {

            if (!response.isOk()) {
                log.warn("Pixabay API 请求失败，状态码: {}", response.getStatus());
                return imageList;
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray hits = result.getJSONArray("hits");
            
            if (hits == null || hits.isEmpty()) {
                log.info("未找到相关插画资源，关键词: {}", query);
                return imageList;
            }

            int actualCount = Math.min(searchCount, hits.size());
            for (int i = 0; i < actualCount; i++) {
                JSONObject hit = hits.getJSONObject(i);
                
                // 获取图片信息
                String tags = hit.getStr("tags", query);
                String webformatURL = hit.getStr("webformatURL");
                String largeImageURL = hit.getStr("largeImageURL");
                
                // 优先使用高质量图片，如果没有则使用web格式
                String imageUrl = StrUtil.isNotBlank(largeImageURL) ? largeImageURL : webformatURL;
                
                if (StrUtil.isNotBlank(imageUrl)) {
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.ILLUSTRATION)
                            .description(tags)
                            .url(imageUrl)
                            .build());
                }
            }
            
            log.info("成功获取 {} 张插画资源，关键词: {}", imageList.size(), query);
            
        } catch (Exception e) {
            log.error("Pixabay插画搜索失败，关键词: {}, 错误: {}", query, e.getMessage(), e);
        }
        
        return imageList;
    }

    /**
     * 搜索矢量图和图标
     */
    @Tool("搜索矢量图和图标，适用于UI设计和网站装饰")
    public List<ImageResource> searchVectorIcons(@P("搜索关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        int searchCount = 8;

        try (HttpResponse response = HttpRequest.get(PIXABAY_API_URL)
                .form("key", pixabayApiKey)
                .form("q", query)
                .form("image_type", "vector")  // 专门搜索矢量图
                .form("category", "backgrounds,fashion,nature,science,education,feelings,health,people,places,animals,industry,computer,food,sports,transportation,travel,buildings,business,music")
                .form("per_page", searchCount)
                .form("page", 1)
                .form("safesearch", "true")
                .form("order", "popular")
                .timeout(15000)
                .execute()) {

            if (!response.isOk()) {
                log.warn("Pixabay矢量图API请求失败，状态码: {}", response.getStatus());
                return imageList;
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray hits = result.getJSONArray("hits");
            
            if (hits != null && !hits.isEmpty()) {
                int actualCount = Math.min(searchCount, hits.size());
                for (int i = 0; i < actualCount; i++) {
                    JSONObject hit = hits.getJSONObject(i);
                    String tags = hit.getStr("tags", query);
                    String webformatURL = hit.getStr("webformatURL");
                    
                    if (StrUtil.isNotBlank(webformatURL)) {
                        imageList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.ILLUSTRATION)
                                .description("矢量图: " + tags)
                                .url(webformatURL)
                                .build());
                    }
                }
            }
            
            log.info("成功获取 {} 张矢量图资源，关键词: {}", imageList.size(), query);
            
        } catch (Exception e) {
            log.error("Pixabay矢量图搜索失败，关键词: {}, 错误: {}", query, e.getMessage(), e);
        }
        
        return imageList;
    }
}
