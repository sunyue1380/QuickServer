package cn.schoolwow.quickserver.domain;

import java.lang.reflect.Method;

/**请求类*/
public class Request {
    /**映射路径*/
    public String mappingUrl;
    /**关联方法*/
    public Method method;
    /**控制器实例*/
    public Object instance;
}
