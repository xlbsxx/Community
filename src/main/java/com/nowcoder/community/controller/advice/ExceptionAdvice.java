package com.nowcoder.community.controller.advice;

import com.nowcoder.community.util.CommunityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@ControllerAdvice(annotations = Controller.class)//只扫描带有Controller注解的bean
public class ExceptionAdvice {
    private static final Logger logger= LoggerFactory.getLogger(ExceptionAdvice.class);
    @ExceptionHandler({Exception.class})//处理异常的方法
    public void handleException(Exception e, HttpServletRequest request, HttpServletResponse response)throws IOException {
        logger.error("服务器发生异常："+e.getMessage());
        for(StackTraceElement element:e.getStackTrace()){
            logger.error(element.toString());
        }
        String xRequestedWith=request.getHeader("x-requested-with");
        if(xRequestedWith.equals("XMLHttpRequest")){//请求是异步请求
            response.setContentType("application/plain;charset=utf-8");//返回普通字符串
            PrintWriter writer=response.getWriter();
            writer.write(CommunityUtil.getJSONString(1,"服务器异常"));
        }else{//普通请求，重定向到500界面
            response.sendRedirect(request.getContextPath()+"/error");
        }
    }
}
