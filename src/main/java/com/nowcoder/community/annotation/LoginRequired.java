package com.nowcoder.community.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)//表示这个注解用来描述方法
@Retention(RetentionPolicy.RUNTIME)//注解在程序运行时有效
public @interface LoginRequired {



}
