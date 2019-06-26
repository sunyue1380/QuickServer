package cn.schoolwow.quickserver.domain;

import cn.schoolwow.quickserver.interceptor.HandlerInterceptor;

/**过滤器类*/
public class Filter {
    /**拦截器路径*/
    public String[] patterns;
    /**拦截器排除路径*/
    public String[] excludePatterns;
    /**拦截器实例*/
    public Class<HandlerInterceptor> handlerInterceptorClass;
    /**拦截器实例*/
    public HandlerInterceptor handlerInterceptor;
}
