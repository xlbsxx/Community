package com.nowcoder.community.controller.interceptor;

import com.nowcoder.community.annotation.LoginRequired;
import com.nowcoder.community.util.HostHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    @Autowired
    private HostHolder hostHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod) {//handler--拦截的目标，是方法就接着处理，不是方法就不要处理
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();//获取拦截到的method对象
            LoginRequired loginRequired = method.getAnnotation(LoginRequired.class);//取method的LoginRequired注解
            if (loginRequired != null && hostHolder.getUser() == null) {//取到了LoginRequired注解，表示这个方法是需要登录才能使用的，那么接下来就判断hostHolder是否持有user
                response.sendRedirect(request.getContextPath() + "/login");//没登录，重定向到登录界面
                return false;//拒绝后续的请求
            }
        }
        return true;
    }
}
