package cn.schoolwow.quickserver.domain;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**请求类*/
public class Request {
    /**映射路径*/
    public String mappingUrl;
    /**Ant模式路径*/
    public String antPatternUrl;
    /**原始模式*/
    public Pattern requestPattern;
    /**正则表达式模式*/
    public Pattern regexUrlPattern;
    /**关联方法*/
    public Method method;
    /**控制器实例*/
    public Object instance;
}
