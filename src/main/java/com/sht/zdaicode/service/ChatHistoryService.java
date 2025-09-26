package com.sht.zdaicode.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.sht.zdaicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.sht.zdaicode.model.entity.ChatHistory;
import com.sht.zdaicode.model.entity.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author 1
 * @since 2025-09-07
 */
public interface ChatHistoryService extends IService<ChatHistory> {


    /**
     * 添加对话历史
     *
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(long appId, String message, String messageType, Long userId);

    /**
     * 根据应用Id删除应用的所有对话历史
     *
     * @param appId 应用Id
     * @return 是否删除成功
     */
    boolean deleteByAppId(long appId);

    /**
     * 加载应用的对话历史到内存
     *
     * @param appId
     * @param chatMemory
     * @param maxCount 最大加载条数
     * @return 加载的条数
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 分页查询应用的对话历史
     *
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}
