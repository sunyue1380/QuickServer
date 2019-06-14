package cn.schoolwow.quickserver.request;

import java.util.HashMap;
import java.util.Map;

/**http请求元信息*/
public class RequestMeta {
    /**请求方法*/
    public String method;
    /**请求路径*/
    public String requestURI;
    /**参数字符串*/
    public String query;
    /**协议*/
    public String protocol;
    //TODO 参数值和头部的键都是有可能重复的
    /**参数列表*/
    public Map<String,String> parameters = new HashMap<>();
    /**http头部*/
    public Map<String,String> headers = new HashMap<>();
    /**Cookie信息*/
    public Map<String,String> cookies = new HashMap<>();
    /**body*/
    public String body;

    /**主体类型*/
    public String contentType;
    /**主体长度*/
    public long contentLength;

    /**http头部*/
    public String connection;
}
