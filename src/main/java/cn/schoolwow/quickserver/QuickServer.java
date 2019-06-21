package cn.schoolwow.quickserver;

import cn.schoolwow.quickserver.annotation.*;
import cn.schoolwow.quickserver.request.MultipartFile;
import cn.schoolwow.quickserver.request.RequestHandler;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseHandler;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionHandler;
import cn.schoolwow.quickserver.session.SessionMeta;
import cn.schoolwow.quickserver.util.ControllerUtil;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QuickServer {
    Logger logger = LoggerFactory.getLogger(QuickServer.class);
    private int port = 9000;
    private ThreadPoolExecutor threadPoolExecutor;
    private String webapps;

    public static QuickServer newInstance(){
        return new QuickServer();
    }

    public QuickServer port(int port){
        this.port = port;
        return this;
    }

    public QuickServer controller(String packageName){
        ControllerUtil.register(packageName);
        return this;
    }

    public QuickServer threadPool(ThreadPoolExecutor threadPoolExecutor){
        this.threadPoolExecutor = threadPoolExecutor;
        return this;
    }

    public QuickServer webapps(String webapps){
        this.webapps = webapps;
        return this;
    }

    /**配置压缩策略*/
    public QuickServer compress(boolean compress){
        if(compress){
            QuickServerConfig.compressSupports = new String[]{"gzip"};
        }else{
            QuickServerConfig.compressSupports = null;
        }
        return this;
    }

    public void start() throws IOException {
        if(threadPoolExecutor==null){
            threadPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),200,1, TimeUnit.MINUTES,new LinkedBlockingQueue<>());
        }
        if(webapps==null){
            webapps = new File(".").getAbsolutePath();
        }
        logger.debug("[webapp]目录地址:{}",webapps);

        ServerSocket serverSocket = new ServerSocket(this.port);
        logger.debug("[服务已启动]地址:http://127.0.0.1:{}",port);
        while(true){
            final Socket socket = serverSocket.accept();
            threadPoolExecutor.execute(()->{
                try {
                    //处理输入流
                    RequestMeta requestMeta = new RequestMeta();
                    requestMeta.inputStream = new BufferedInputStream(socket.getInputStream());
                    RequestHandler.parseRequest(requestMeta);
                    //处理输出流
                    ResponseMeta responseMeta = new ResponseMeta();
                    responseMeta.protocol = requestMeta.protocol;
                    responseMeta.outputStream = new BufferedOutputStream(socket.getOutputStream());
                    SessionMeta sessionMeta = SessionHandler.handleRequest(requestMeta,responseMeta);
                    handleRequest(requestMeta,responseMeta,sessionMeta);
                    handleResponse(requestMeta,responseMeta,sessionMeta);

                    ResponseHandler.handleResponse(requestMeta,responseMeta);
                    requestMeta.inputStream.close();
                    responseMeta.outputStream.close();
                    socket.close();
                    logger.info("[请求完毕]======================================================");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /**处理响应*/
    public void handleResponse(RequestMeta requestMeta,ResponseMeta responseMeta,SessionMeta sessionMeta) throws Exception {
        //处理重定向
        if(null!=responseMeta.forward){
            requestMeta.requestURI = responseMeta.forward;
            handleRequest(requestMeta,responseMeta,sessionMeta);
        }

        if(responseMeta.body!=null){
            responseMeta.headers.put("Content-Type",responseMeta.contentType+"; charset="+responseMeta.charset);
            responseMeta.headers.put("Content-Length",responseMeta.body.getBytes().length+"");
        }
        responseMeta.headers.put("Connection","Close");
        responseMeta.headers.put("Server","QuickServer");
        responseMeta.headers.put("Date",new Date().toString());
        if(responseMeta.file==null){
            logger.debug("[响应元数据]响应行:{},头部:{},主体:{}", responseMeta.status+" "+responseMeta.statusMessage,responseMeta.headers,responseMeta.body);
        }else{
            logger.debug("[响应元数据]响应行:{},头部:{},资源:{}", responseMeta.status+" "+responseMeta.statusMessage,responseMeta.headers,responseMeta.file.getAbsolutePath());
        }
    }

    /**处理请求*/
    public void handleRequest(RequestMeta requestMeta,ResponseMeta responseMeta,SessionMeta sessionMeta) throws Exception {
        handleStaticFile(requestMeta,responseMeta);
        if(responseMeta.file!=null){
            return;
        }
        //查找请求路径
        Method method = ControllerUtil.getMethod(requestMeta.requestURI);
        if(method==null){
            responseMeta.response(ResponseMeta.HttpStatus.NOT_FOUND);
            return;
        }
        requestMeta.invokeMethod = method;
        //判断是否支持该方法
        RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
        RequestMethod[] requestMethods = methodRequestMapping.method();
        boolean support = false;
        if(requestMethods.length==0||(requestMeta.origin!=null&&"OPTIONS".equalsIgnoreCase(requestMeta.method))){
            support = true;
        }else{
            for(RequestMethod requestMethod:requestMethods){
                if(requestMethod.name().equals(requestMeta.method)){
                    support = true;
                    break;
                }
            }
        }
        if(!support){
            responseMeta.response(ResponseMeta.HttpStatus.METHOD_NOT_ALLOWED);
            return;
        }
        //处理跨域请求
        handleCrossOrigin(requestMeta,responseMeta);
        //判断是否是跨域预检请求
        if(requestMeta.origin!=null&&"OPTIONS".equalsIgnoreCase(requestMeta.method)){
            responseMeta.status = 200;
            responseMeta.statusMessage = "OK";
            return;
        }
        //方法调用
        logger.debug("[调用方法]请求路径:[{}],调用方法:{}",requestMeta.method+" "+requestMeta.requestURI,method.toString());
        Object result = handleInvokeMethod(requestMeta,responseMeta,sessionMeta);
        if(responseMeta.status==0){
            responseMeta.response(ResponseMeta.HttpStatus.OK);
        }
        if(responseMeta.contentType==null){
            responseMeta.contentType = "text/plain;";
        }
        if(result!=null){
            if(responseMeta.body==null){
                responseMeta.body = result.toString();
            }else{
                responseMeta.body += result.toString();
            }
        }
    }

    /**处理方法调用*/
    private Object handleInvokeMethod(RequestMeta requestMeta,ResponseMeta responseMeta,SessionMeta sessionMeta) throws Exception {
        Object controller = requestMeta.invokeMethod.getDeclaringClass().newInstance();
        Parameter[] parameters = requestMeta.invokeMethod.getParameters();
        if(parameters.length==0){
            return requestMeta.invokeMethod.invoke(controller);
        }
        List<Object> parameterList = new ArrayList<>();
        for(Parameter parameter:parameters){
            String parameterType = parameter.getType().getName();
            switch(parameterType){
                case "cn.schoolwow.quickserver.request.RequestMeta":{
                    parameterList.add(requestMeta);
                }break;
                case "cn.schoolwow.quickserver.response.ResponseMeta":{
                    parameterList.add(responseMeta);
                }break;
                case "cn.schoolwow.quickserver.session.SessionMeta":{
                    parameterList.add(sessionMeta);
                }break;
                default:{
                    //处理RequestParam
                    {
                        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                        if(null!=requestParam){
                            String requestParameter = requestMeta.parameters.get(requestParam.name());
                            if(requestParam.required()&&requestParameter==null){
                                responseMeta.response(ResponseMeta.HttpStatus.BAD_REQUEST);
                                responseMeta.body = "请求参数["+requestParam.name()+"]不能为空!";
                                return null;
                            }
                            if(requestParameter==null){
                                requestParameter = requestParam.defaultValue();
                            }
                            parameterList.add(parameter.getType().getConstructor(String.class).newInstance(requestParameter));
                        }
                    }
                    //处理RequestPart
                    {
                        RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
                        if(null!=requestPart){
                            MultipartFile multipartFile = requestMeta.fileParameters.get(requestPart.name());
                            if(requestPart.required()&&multipartFile==null){
                                responseMeta.response(ResponseMeta.HttpStatus.BAD_REQUEST);
                                responseMeta.body = "请求参数["+requestPart.name()+"]不能为空!";
                                return null;
                            }
                            parameterList.add(multipartFile);
                        }
                    }
                    //处理RequestBody
                    {
                        RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
                        if(null!=requestBody){
                            if("java.lang.String".equals(parameterType)){
                                parameterList.add(requestMeta.body);
                            }else if("java.io.InputStream".equals(parameterType)){
                                parameterList.add(requestMeta.inputStream);
                            }
                        }
                    }
                    //处理SessionAttribute
                    {
                        SessionAttribute sessionAttribute = parameter.getAnnotation(SessionAttribute.class);
                        if(null!=sessionAttribute){
                            parameterList.add(parameter.getType().getConstructor(String.class).newInstance(sessionMeta.attributes.get(sessionAttribute.name())));
                        }
                    }
                    //处理CookieValue
                    {
                        CookieValue cookieValue = parameter.getAnnotation(CookieValue.class);
                        if(null!=cookieValue){
                            parameterList.add(requestMeta.cookies.get(cookieValue.name()));
                        }
                    }
                    //处理RequestHeader
                    {
                        RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
                        if(null!=requestHeader){
                            parameterList.add(requestMeta.headers.get(requestHeader.name().toLowerCase()));
                        }
                    }
                }
            }
        }
        return requestMeta.invokeMethod.invoke(controller,parameterList.toArray(new Object[]{parameterList.size()}));
    }

    /**处理跨域请求*/
    private void handleCrossOrigin(RequestMeta requestMeta,ResponseMeta responseMeta){
        CrossOrigin crossOrigin = requestMeta.invokeMethod.getDeclaredAnnotation(CrossOrigin.class);
        if(null==crossOrigin){
            return;
        }
        //检查origin
        boolean allowOrigin = false;
        if(crossOrigin.origins().length==0){
            allowOrigin = true;
        }else{
            String[] origins = crossOrigin.origins();
            for(String origin:origins){
                if(requestMeta.origin.equalsIgnoreCase(origin)||"*".equalsIgnoreCase(origin)){
                    break;
                }
            }
        }
        if(!allowOrigin){
            return;
        }
        //检查请求方法
        if(crossOrigin.methods().length>0&&requestMeta.headers.containsKey("access-control-request-method")){
            String requestMethod = requestMeta.headers.get("access-control-request-method");
            boolean allowMethod = false;
            for(String method:crossOrigin.methods()){
                if(requestMethod.equalsIgnoreCase(method)){
                    allowMethod = true;
                    break;
                }
            }
            if(!allowMethod){
                return;
            }
        }
        //检查请求头部
        if(crossOrigin.headers().length>0&&requestMeta.headers.containsKey("access-control-request-headers")){
            String[] requestHeaders = requestMeta.headers.get("access-control-request-headers").split(",");
            for(String allowHeader:crossOrigin.headers()){
                boolean exist = false;
                for(String requestHeader:requestHeaders){
                    if(requestHeader.equals(allowHeader)){
                        exist = true;
                        break;
                    }
                }
                if(!exist){
                    return;
                }
            }
        }
        //缓存跨域响应头部信息
        if(!ControllerUtil.crossOriginMap.containsKey(requestMeta.requestURI)){
            //设置跨域头部信息
            Map<String,String> crossOriginMap = new HashMap<>();
            crossOriginMap.put("Access-Control-Allow-Origin",(crossOrigin.origins().length==0?"*":requestMeta.origin));
            crossOriginMap.put("Access-Control-Max-Age",crossOrigin.maxAge()+"");
            if(crossOrigin.allowCredentials()){
                crossOriginMap.put("Access-Control-Allow-Credentials","true");
            }
            if(crossOrigin.methods().length>0){
                StringBuffer stringBuffer = new StringBuffer();
                for(String method:crossOrigin.methods()){
                    stringBuffer.append(method+",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length()-1);
                crossOriginMap.put("Access-Control-Allow-Methods",stringBuffer.toString());
            }else{
                crossOriginMap.put("Access-Control-Allow-Methods",requestMeta.headers.get("access-control-request-method"));
            }
            if(crossOrigin.headers().length>0){
                StringBuffer stringBuffer = new StringBuffer();
                for(String header:crossOrigin.headers()){
                    stringBuffer.append(header+",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length()-1);
                crossOriginMap.put("Access-Control-Allow-Headers",stringBuffer.toString());
            }else{
                crossOriginMap.put("Access-Control-Allow-Headers",requestMeta.headers.get("access-control-request-headers"));
            }
            if(crossOrigin.exposedHeaders().length>0){
                StringBuffer stringBuffer = new StringBuffer();
                for(String exposedHeader:crossOrigin.exposedHeaders()){
                    stringBuffer.append(exposedHeader+",");
                }
                stringBuffer.deleteCharAt(stringBuffer.length()-1);
                crossOriginMap.put("Access-Control-Expose-Headers",stringBuffer.toString());
            }
            ControllerUtil.crossOriginMap.put(requestMeta.requestURI,crossOriginMap);
        }
        responseMeta.headers.putAll(ControllerUtil.crossOriginMap.get(requestMeta.requestURI));
    }

    /**处理静态资源请求*/
    private void handleStaticFile(RequestMeta requestMeta,ResponseMeta responseMeta) throws IOException {
        //查找静态路径
        File staticFile = new File(webapps+requestMeta.requestURI);
        if(staticFile.exists()&&staticFile.isFile()){
            logger.debug("[访问静态资源]请求路径:{},资源路径:{}",requestMeta.requestURI,staticFile.getAbsolutePath());
            responseMeta.response(ResponseMeta.HttpStatus.OK);
            responseMeta.file = staticFile;
            responseMeta.contentType = Files.probeContentType(Paths.get(staticFile.getAbsolutePath()));
            responseMeta.contentLength = responseMeta.file.length();
            responseMeta.headers.put("Content-Type",responseMeta.contentType);
            responseMeta.headers.put("Content-Length",responseMeta.contentLength+"");
            return;
        }
    }
}
