package cn.schoolwow.quickserver.request;

import cn.schoolwow.quickserver.domain.Request;

import java.io.InputStream;
import java.lang.reflect.Method;
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
    /**编码格式*/
    public String charset = "utf-8";
    /**路径映射参数*/
    public Map<String,String> pathVariable = new HashMap<>();
    //TODO 参数值和头部的键都是有可能重复的
    /**参数列表*/
    public Map<String,String> parameters = new HashMap<>();
    /**文件参数列表*/
    public Map<String,MultipartFile> fileParameters = new HashMap<>();
    /**http头部*/
    public Map<String,String> headers = new HashMap<>();
    /**Cookie列表*/
    public Map<String,String> cookies = new HashMap<>();
    /**boundary*/
    public String boundary;
    /**body*/
    public String body;
    /**主体类型*/
    public String contentType;
    /**主体长度*/
    public long contentLength;
    /**origin头部*/
    public String origin;
    /**http头部*/
    public String connection;
    /**认证头*/
    public String authorization;
    /**允许的压缩格式*/
    public String acceptEncoding;
    /**原始输入流*/
    public InputStream inputStream;

    /**调用方法*/
    public Method invokeMethod;
    /**对应Request方法*/
    public Request request;
}
