package cn.schoolwow.quickserver;

import cn.schoolwow.quickserver.annotation.*;
import cn.schoolwow.quickserver.domain.Filter;
import cn.schoolwow.quickserver.domain.Request;
import cn.schoolwow.quickserver.request.MultipartFile;
import cn.schoolwow.quickserver.request.RequestHandler;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseHandler;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.session.SessionHandler;
import cn.schoolwow.quickserver.session.SessionMeta;
import cn.schoolwow.quickserver.util.AntPatternUtil;
import cn.schoolwow.quickserver.util.ControllerUtil;
import cn.schoolwow.quickserver.util.MIMEUtil;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.net.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class QuickServer {
    Logger logger = LoggerFactory.getLogger(QuickServer.class);
    private int port = 9000;
    private ThreadPoolExecutor threadPoolExecutor;

    public static QuickServer newInstance(){
        return new QuickServer();
    }

    public QuickServer port(int port){
        this.port = port;
        return this;
    }

    public QuickServer scan(String packageName){
        ControllerUtil.scan(packageName);
        return this;
    }

    public QuickServer register(Class _class){
        ControllerUtil.register(_class);
        return this;
    }

    public QuickServer threadPool(ThreadPoolExecutor threadPoolExecutor){
        this.threadPoolExecutor = threadPoolExecutor;
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
        //注入依赖
        ControllerUtil.refresh();

        ServerSocket serverSocket = new ServerSocket(this.port);
        logger.info("[服务已启动]地址:http://127.0.0.1:{}",port);
        while(true){
            final Socket socket = serverSocket.accept();
            threadPoolExecutor.execute(()->{
                final RequestMeta requestMeta = new RequestMeta();
                requestMeta.remoteAddress = socket.getInetAddress();
                final ResponseMeta responseMeta = new ResponseMeta();
                try {
                    //处理输入流
                    requestMeta.inputStream = new BufferedInputStream(socket.getInputStream());
                    RequestHandler.parseRequest(requestMeta);
                    //处理输出流
                    responseMeta.protocol = requestMeta.protocol;
                    responseMeta.outputStream = new BufferedOutputStream(socket.getOutputStream());
                    SessionMeta sessionMeta = SessionHandler.handleRequest(requestMeta,responseMeta);
                    handleRequest(requestMeta,responseMeta,sessionMeta);
                    handleResponse(requestMeta,responseMeta,sessionMeta);
                    //输出返回信息到流中
                    ResponseHandler.handleResponse(requestMeta,responseMeta);
                    requestMeta.inputStream.close();
                    responseMeta.outputStream.close();
                    socket.close();
                    logger.debug("[请求完毕]======================================================");
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        responseMeta.response(ResponseMeta.HttpStatus.INTERNAL_SERVER_ERROR,requestMeta);
                        ResponseHandler.handleResponse(requestMeta,responseMeta);
                        socket.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
    }

    /**处理响应*/
    private void handleResponse(RequestMeta requestMeta,ResponseMeta responseMeta,SessionMeta sessionMeta) throws Exception {
        //处理过滤器
        //TODO 按照匹配模式长度进行顺序执行
        for(Filter filter: ControllerUtil.filterList){
            if(AntPatternUtil.matchFilter(requestMeta.requestURI,filter)){
                filter.handlerInterceptor.postHandle(requestMeta,responseMeta,sessionMeta,requestMeta.invokeMethod,responseMeta.body);
            }
        }
        //处理重定向
        if(null!=responseMeta.forward){
            requestMeta.requestURI = responseMeta.forward;
            requestMeta.invokeMethod = null;
            handleRequest(requestMeta,responseMeta,sessionMeta);
        }
        if(responseMeta.body!=null){
            responseMeta.contentLength = responseMeta.body.getBytes().length;
        }
        responseMeta.headers.put("Content-Type",responseMeta.contentType+"; charset="+responseMeta.charset);
        responseMeta.headers.put("Content-Length",responseMeta.contentLength+"");
        responseMeta.headers.put("Connection","Close");
        responseMeta.headers.put("Server","QuickServer");
        responseMeta.headers.put("Date",new Date().toString());
        if(responseMeta.staticURL==null){
            logger.debug("[响应元数据]响应行:{},头部:{},主体:{}", responseMeta.status+" "+responseMeta.statusMessage,responseMeta.headers,responseMeta.body);
        }else{
            logger.debug("[响应元数据]响应行:{},头部:{},资源:{}", responseMeta.status+" "+responseMeta.statusMessage,responseMeta.headers,responseMeta.staticURL);
        }
        for(Filter filter: ControllerUtil.filterList){
            if(AntPatternUtil.matchFilter(requestMeta.requestURI,filter)){
                filter.handlerInterceptor.afterCompletion(requestMeta,responseMeta,sessionMeta,requestMeta.invokeMethod);
            }
        }
    }

    /**处理请求*/
    private void handleRequest(RequestMeta requestMeta,ResponseMeta responseMeta,SessionMeta sessionMeta) throws Exception {
        //初始化过滤器
        {
            for(Filter filter:ControllerUtil.filterList){
                if(AntPatternUtil.matchFilter(requestMeta.requestURI,filter)){
                    filter.handlerInterceptor = ControllerUtil.getInterceptor(filter.handlerInterceptorClass);
                }
            }
        }
        //初始化Method
        {
            getInvokeMethod(requestMeta);
        }
        //TODO 按照匹配模式长度进行顺序执行
        for(Filter filter:ControllerUtil.filterList){
            if(AntPatternUtil.matchFilter(requestMeta.requestURI,filter)){
                if(!filter.handlerInterceptor.preHandle(requestMeta,responseMeta,sessionMeta,requestMeta.invokeMethod)){
                    return;
                }
            }
        }
        //处理静态资源
        {
            handleStaticFile(requestMeta,responseMeta);
            if(responseMeta.staticURL!=null){
                return;
            }
        }
        //判断请求路径
        {
            if(requestMeta.invokeMethod==null){
                responseMeta.response(ResponseMeta.HttpStatus.NOT_FOUND,requestMeta);
                return;
            }
        }
        //判断是否支持该方法
        {
            RequestMethod[] requestMethods = requestMeta.request.requestMethods;
            boolean support = false;
            if(requestMethods.length==0||(requestMeta.origin!=null&&RequestMethod.OPTIONS.name().equalsIgnoreCase(requestMeta.method))){
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
                responseMeta.response(ResponseMeta.HttpStatus.METHOD_NOT_ALLOWED,requestMeta);
                return;
            }
        }
        //处理Basic Auth
        {
            BasicAuth basicAuth = requestMeta.invokeMethod.getAnnotation(BasicAuth.class);
            if(basicAuth==null){
                basicAuth = requestMeta.invokeMethod.getDeclaringClass().getAnnotation(BasicAuth.class);
            }
            if(basicAuth!=null){
                if(requestMeta.authorization==null){
                    responseMeta.response(ResponseMeta.HttpStatus.UNAUTHORIZED,requestMeta);
                    responseMeta.headers.put("WWW-Authenticate","Basic realm=\""+basicAuth.realm()+"\"");
                    return;
                }
                String auth = requestMeta.authorization.substring(requestMeta.authorization.indexOf("Basic ")+6);
                String targetAuth = new String(Base64.getEncoder().encode((basicAuth.username()+":"+basicAuth.password()).getBytes()));
                if(!targetAuth.equals(auth)){
                    responseMeta.response(ResponseMeta.HttpStatus.UNAUTHORIZED,requestMeta);
                    responseMeta.headers.put("WWW-Authenticate","Basic realm=\""+basicAuth.realm()+"\"");
                    return;
                }
            }
        }
        //处理跨域请求
        {
            handleCrossOrigin(requestMeta,responseMeta);
            //判断是否是跨域预检请求
            if(requestMeta.origin!=null&&RequestMethod.OPTIONS.name().equalsIgnoreCase(requestMeta.method)){
                responseMeta.status = 200;
                responseMeta.statusMessage = "OK";
                return;
            }
        }
        //方法调用
        Object result = null;
        {
            logger.debug("[调用方法]请求路径:[{}],调用方法:{}",requestMeta.method+" "+requestMeta.requestURI,requestMeta.invokeMethod.toString());
            result = handleInvokeMethod(requestMeta,responseMeta,sessionMeta);
            if(responseMeta.status==0){
                responseMeta.response(ResponseMeta.HttpStatus.OK,requestMeta);
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
    }

    /**获取InvokeMethod*/
    private void getInvokeMethod(RequestMeta requestMeta){
        Collection<Request> requestCollection = ControllerUtil.requestMappingHandler.values();
        for(Request request:requestCollection){
            int urlPos=0,mappingPos=0,lastUrlPos=0,lastMappingPos=0;
            String requestURI = requestMeta.requestURI;
            String mappingUrl = request.mappingUrl;
            String antRequestUrl = mappingUrl;
            while(urlPos<requestURI.length()&&mappingPos<mappingUrl.length()){
                if(mappingUrl.charAt(mappingPos)=='{'){
                    lastUrlPos=urlPos;
                    lastMappingPos=mappingPos+1;

                    while(mappingPos<mappingUrl.length()&&mappingUrl.charAt(mappingPos)!='}'){
                        mappingPos++;
                    }
                    if(mappingPos<mappingUrl.length()){
                        //提取变量名
                        String name = mappingUrl.substring(lastMappingPos,mappingPos);
                        antRequestUrl = antRequestUrl.replace("{"+name+"}","*");
                        String value = null;
                        //提取变量值
                        if(mappingPos+1<mappingUrl.length()){
                            while(urlPos<requestURI.length()&&requestURI.charAt(urlPos)!=mappingUrl.charAt(mappingPos+1)){
                                urlPos++;
                            }
                            if(urlPos<requestURI.length()){
                                value = requestURI.substring(lastUrlPos,urlPos);
                            }
                        }else{
                            value = requestURI.substring(lastUrlPos);
                        }
                        requestMeta.pathVariable.put(name,value);
                    }
                }else if(requestURI.charAt(urlPos)==mappingUrl.charAt(mappingPos)){
                    urlPos++;
                    mappingPos++;
                }else{
                    mappingPos++;
                }
            }
            if((mappingUrl.contains("{")&&AntPatternUtil.doMatch(requestURI,antRequestUrl))
                    ||requestURI.equals(mappingUrl)){
                if(request.requestMethods.length==0){
                    requestMeta.invokeMethod = request.method;
                }else{
                    //判断请求方法是否匹配
                    for(RequestMethod requestMethod:request.requestMethods){
                        if(requestMethod.name().equals(requestMeta.method)){
                            requestMeta.invokeMethod = request.method;
                            break;
                        }
                    }
                }
            }
            if(requestMeta.invokeMethod==null){
                continue;
            }
            requestMeta.request = request;
            break;
        }
    }

    /**处理方法调用*/
    private Object handleInvokeMethod(RequestMeta requestMeta,ResponseMeta responseMeta,SessionMeta sessionMeta) throws Exception {
        Object controller = requestMeta.request.instance;
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
                                responseMeta.response(ResponseMeta.HttpStatus.BAD_REQUEST,requestMeta);
                                responseMeta.body = "请求参数["+requestParam.name()+"]不能为空!";
                                return null;
                            }
                            if(requestParameter==null){
                                requestParameter = requestParam.defaultValue();
                            }
                            parameterList.add(ControllerUtil.castParameter(parameter,requestParameter,requestParam.pattern()));
                        }
                    }
                    //处理RequestPart
                    {
                        RequestPart requestPart = parameter.getAnnotation(RequestPart.class);
                        if(null!=requestPart){
                            MultipartFile multipartFile = requestMeta.fileParameters.get(requestPart.name());
                            if(requestPart.required()&&multipartFile==null){
                                responseMeta.response(ResponseMeta.HttpStatus.BAD_REQUEST,requestMeta);
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
                            }else if("com.alibaba.fastjson.JSONObject".equals(parameterType)){
                                parameterList.add(JSON.parseObject(requestMeta.body));
                            }else if("com.alibaba.fastjson.JSONArray".equals(parameterType)){
                                parameterList.add(JSON.parseArray(requestMeta.body));
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
                    //处理PathVariable
                    {
                        PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
                        if(null!=pathVariable){
                            if(pathVariable.required()&&!requestMeta.pathVariable.containsKey(pathVariable.name())){
                                responseMeta.response(ResponseMeta.HttpStatus.BAD_REQUEST,requestMeta);
                                responseMeta.body = "路径变量["+pathVariable.name()+"]不能为空!";
                                return null;
                            }
                            parameterList.add(ControllerUtil.castParameter(parameter,requestMeta.pathVariable.get(pathVariable.name()),null));
                        }
                    }
                }
            }
        }
        Object result = requestMeta.invokeMethod.invoke(controller,parameterList.toArray(new Object[]{parameterList.size()}));
        if(result==null){
            return null;
        }
        //处理ResponseBody注解
        ResponseBody responseBody = controller.getClass().getDeclaredAnnotation(ResponseBody.class);
        if(responseBody==null){
            responseBody = requestMeta.invokeMethod.getAnnotation(ResponseBody.class);
        }
        if(responseBody==null){
            responseMeta.forward(result.toString());
        }else{
            switch(responseBody.value()){
                case String:{
                }break;
                case JSON:{
                    result = JSON.toJSONString(result);
                    responseMeta.contentType = "application/json";
                }
            }
        }
        return result;
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
    private void handleStaticFile(RequestMeta requestMeta,ResponseMeta responseMeta) throws IOException, URISyntaxException {
        URL url = this.getClass().getResource(requestMeta.requestURI);
        if(url==null){
            return;
        }
        switch(url.getProtocol()){
            case "file":{
                File file = new File(url.getFile());
                if(file.isDirectory()){
                    return;
                }
                responseMeta.inputStream = url.openStream();
            }break;
            case "jar":{
                JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                JarFile jarFile = jarURLConnection.getJarFile();
                JarEntry jarEntry = jarFile.getJarEntry(requestMeta.requestURI.substring(1));
                if(jarEntry==null||jarEntry.isDirectory()){
                    return;
                }
                responseMeta.inputStream = jarFile.getInputStream(jarEntry);
            }break;
        }
        logger.debug("[访问静态资源]请求路径:{},资源路径:{}",requestMeta.requestURI,url.toString());
        responseMeta.response(ResponseMeta.HttpStatus.OK,requestMeta);
        responseMeta.staticURL = url;
        responseMeta.contentType = MIMEUtil.getMIMEType(requestMeta.requestURI);
        responseMeta.contentLength = responseMeta.inputStream.available();
    }
}
