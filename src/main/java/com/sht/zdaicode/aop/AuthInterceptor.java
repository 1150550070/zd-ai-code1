package com.sht.zdaicode.aop;

import com.sht.zdaicode.annotation.AuthCheck;
import com.sht.zdaicode.constant.UserConstant;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.model.entity.User;
import com.sht.zdaicode.model.enums.UserRoleEnum;
import com.sht.zdaicode.model.vo.LoginUserVO;
import com.sht.zdaicode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;
    /**
     * 拦截
     *
     * @param joinPoint
     * @param authCheck
     * @return
     * @throws Throwable
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint,AuthCheck authCheck) throws Throwable {
        // 1. 从注解中获取必须的角色
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 2. 从请求中获取登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 3.不需要权限,放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 4.以下的代码,必须有这个权限才能通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if(userRoleEnum == null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //必须有管理员权限,但当前登录用户没有
        if(UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        //通过普通用户的权限校验,放行
        return joinPoint.proceed();
    }

}
