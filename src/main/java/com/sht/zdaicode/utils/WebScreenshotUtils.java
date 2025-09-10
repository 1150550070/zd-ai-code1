package com.sht.zdaicode.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

@Slf4j
public class WebScreenshotUtils {

    private static final WebDriver webDriver;

    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 网页URL
     * @return 压缩后的截图文件路径，失败返回null
     */
    public static String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        
        // 检查webDriver是否初始化成功
        if (webDriver == null) {
            log.error("WebDriver未初始化，无法进行截图");
            return null;
        }
        
        try {
            log.info("开始截图，URL: {}", webUrl);
            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            log.info("创建临时目录: {}", rootPath);
            
            // 图片后缀
            final String IMAGE_SUFFIX = ".png";
            // 原始截图文件路径
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            
            // 访问网页
            log.info("访问网页: {}", webUrl);
            webDriver.get(webUrl);
            
            // 等待页面加载完成
            log.info("等待页面加载完成...");
            waitForPageLoad(webDriver);
            
            // 截图
            log.info("开始截图...");
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            
            // 保存原始图片
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            
            // 压缩图片
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            
            // 删除原始图片，只保留压缩图片
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}, 错误详情: {}", webUrl, e.getMessage(), e);
            return null;
        }
    }



    /**
     * 初始化浏览器驱动（只使用Edge浏览器）
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            log.info("初始化 Edge 浏览器...");
            return initEdge(width, height);
        } catch (Exception e) {
            log.error("Edge 浏览器初始化失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Edge浏览器初始化失败");
        }
    }



    /**
     * 初始化 Edge 浏览器
     */
    private static WebDriver initEdge(int width, int height) {
        try {
            // 尝试使用WebDriverManager自动管理EdgeDriver
            WebDriverManager.edgedriver().setup();
            log.info("WebDriverManager 设置 EdgeDriver 成功");
        } catch (Exception e) {
            log.warn("WebDriverManager 设置 EdgeDriver 失败，尝试使用系统默认路径: {}", e.getMessage());
            // 如果WebDriverManager失败，设置系统属性指向EdgeDriver路径
            // Windows系统EdgeDriver通常在以下路径之一
            String[] possiblePaths = {
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedgedriver.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedgedriver.exe",
                System.getProperty("user.home") + "\\AppData\\Local\\Microsoft\\Edge\\Application\\msedgedriver.exe"
            };
            
            boolean driverFound = false;
            for (String path : possiblePaths) {
                if (new java.io.File(path).exists()) {
                    System.setProperty("webdriver.edge.driver", path);
                    log.info("找到 EdgeDriver 路径: {}", path);
                    driverFound = true;
                    break;
                }
            }
            
            if (!driverFound) {
                log.warn("未找到 EdgeDriver，尝试使用系统PATH中的驱动");
            }
        }
        
        // 配置 Edge 选项
        EdgeOptions options = new EdgeOptions();
        // 无头模式
        options.addArguments("--headless");
        // 禁用GPU（在某些环境下避免问题）
        options.addArguments("--disable-gpu");
        // 禁用沙盒模式（Docker环境需要）
        options.addArguments("--no-sandbox");
        // 禁用开发者shm使用
        options.addArguments("--disable-dev-shm-usage");
        // 设置窗口大小
        options.addArguments(String.format("--window-size=%d,%d", width, height));
        // 禁用扩展
        options.addArguments("--disable-extensions");
        // 设置用户代理
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36 Edg/91.0.864.59");
        
        // 创建驱动
        WebDriver driver = new EdgeDriver(options);
        // 设置页面加载超时
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        // 设置隐式等待
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        log.info("Edge 浏览器初始化成功");
        return driver;
    }

    /**
     * 保存图片到文件
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        // 压缩图片质量（0.1 = 10% 质量）
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            // 创建等待页面加载对象
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // 等待 document.readyState 为complete
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 额外等待一段时间，确保动态内容加载完成
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }


}

