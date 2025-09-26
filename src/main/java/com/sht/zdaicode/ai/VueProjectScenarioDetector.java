package com.sht.zdaicode.ai;

import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.model.enums.VueProjectScenarioEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Vue项目场景检测器
 * 根据用户消息和项目状态判断是创建还是修改模式
 */
@Slf4j
@Component
public class VueProjectScenarioDetector {

    /**
     * 修改相关的关键词模式
     */
    private static final Pattern EDIT_KEYWORDS = Pattern.compile(
            ".*(修改|更改|改成|替换|调整|编辑|变更|换成|改为|更新|删除|添加|增加|移除).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 创建相关的关键词模式
     */
    private static final Pattern CREATE_KEYWORDS = Pattern.compile(
            ".*(创建|生成|制作|开发|构建|新建|做一个|建立|设计).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 检测Vue项目场景
     *
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @return 场景枚举
     */
    public VueProjectScenarioEnum detectScenario(Long appId, String userMessage) {
        // 1. 检查项目是否已存在
        boolean projectExists = checkProjectExists(appId);
        
        log.debug("检测Vue项目场景 - appId: {}, 项目存在: {}, 用户消息: {}", appId, projectExists, userMessage);

        // 2. 如果项目不存在，肯定是创建模式
        if (!projectExists) {
            log.info("项目不存在，使用创建模式");
            return VueProjectScenarioEnum.CREATE;
        }

        // 3. 项目存在时，根据用户消息判断意图
        if (EDIT_KEYWORDS.matcher(userMessage).matches()) {
            log.info("检测到修改关键词，使用修改模式");
            return VueProjectScenarioEnum.EDIT;
        }

        if (CREATE_KEYWORDS.matcher(userMessage).matches()) {
            log.info("检测到创建关键词，但项目已存在，使用修改模式");
            return VueProjectScenarioEnum.EDIT;
        }

        // 4. 默认情况：项目存在且无明确关键词，判断为修改模式
        log.info("项目已存在且无明确关键词，默认使用修改模式");
        return VueProjectScenarioEnum.EDIT;
    }

    /**
     * 检查Vue项目是否已存在
     *
     * @param appId 应用ID
     * @return 是否存在
     */
    private boolean checkProjectExists(Long appId) {
        String projectDirName = "vue_project_" + appId;
        String projectDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName;
        File projectDir = new File(projectDirPath);
        
        // 检查项目目录是否存在且包含关键文件
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return false;
        }

        // 检查是否包含Vue项目的关键文件
        File packageJson = new File(projectDir, "package.json");
        File srcDir = new File(projectDir, "src");
        
        boolean exists = packageJson.exists() && srcDir.exists() && srcDir.isDirectory();
        log.debug("项目目录检查 - 路径: {}, package.json存在: {}, src目录存在: {}", 
                projectDirPath, packageJson.exists(), srcDir.exists() && srcDir.isDirectory());
        
        return exists;
    }
}