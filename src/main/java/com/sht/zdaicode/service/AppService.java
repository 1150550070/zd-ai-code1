package com.sht.zdaicode.service;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.mybatisflex.core.paginate.Page;
import com.sht.zdaicode.model.dto.app.AppAddRequest;
import com.sht.zdaicode.model.dto.app.AppQueryRequest;
import com.sht.zdaicode.model.entity.App;
import com.sht.zdaicode.model.entity.User;
import com.sht.zdaicode.model.vo.AppVO;
import com.sht.zdaicode.model.vo.UserVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author 1
 * @since 2025-09-06
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用 vo
     *
     * @param app 应用
     * @return 应用 vo
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用 vo 列表
     *
     * @param appList 应用列表
     * @return 应用 vo 列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 获取查询条件
     *
     * @param appQueryRequest 应用查询参数
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);



    /**
     * 应用对话
     *
     * @param appId     应用 ID
     * @param message   用户消息
     * @param loginUser 登录用户
     * @param agent     是否为智能体
     * @return 代码生成流
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser , boolean agent);

    /**
     * 部署应用
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 部署 URL
     */
    String deployApp(Long appId, User loginUser);


    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 分页获取精选应用列表（用于缓存预热）
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    Page<AppVO> listGoodAppVOByPage(AppQueryRequest appQueryRequest);
}
