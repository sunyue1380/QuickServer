package cn.schoolwow.quickserver;

import cn.schoolwow.quickserver.annotation.RequestMapping;
import cn.schoolwow.quickserver.annotation.RequestMethod;
import cn.schoolwow.quickserver.annotation.RequestParam;
import cn.schoolwow.quickserver.request.RequestHandler;
import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.response.ResponseHandler;
import cn.schoolwow.quickserver.response.ResponseMeta;
import cn.schoolwow.quickserver.util.ControllerUtil;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()),8192);
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream(),8192);
            threadPoolExecutor.execute(()->{
                //如果是长连接,则一直保持
                while(!socket.isClosed()){
                    try {
                        RequestMeta requestMeta = RequestHandler.parseRequest(br);
                        ResponseMeta responseMeta = new ResponseMeta();
                        responseMeta.protocol = requestMeta.protocol;
                        handleRequest(requestMeta,responseMeta);
                        if(responseMeta.body!=null){
                            responseMeta.headers.put("Content-Type",responseMeta.contentType+"; charset="+responseMeta.charset);
                            responseMeta.headers.put("Content-Length",responseMeta.body.getBytes().length+"");
                        }
                        responseMeta.headers.put("Connection","keep-alive");
                        if(responseMeta.file==null){
                            logger.debug("[响应元数据]响应行:{},头部:{},主体:{}", responseMeta.status+" "+responseMeta.statusMessage,responseMeta.headers,responseMeta.body);
                        }else{
                            logger.debug("[响应元数据]响应行:{},头部:{},资源:{}", responseMeta.status+" "+responseMeta.statusMessage,responseMeta.headers,responseMeta.file.getAbsolutePath());
                        }
                        socket.setKeepAlive(false);
                        if("Keep-Alive".equalsIgnoreCase(requestMeta.connection)){
                            socket.setKeepAlive(true);
                        }
                        if(socket.getKeepAlive()){
                            responseMeta.headers.put("Connection","Keep-Alive");
                        }
                        ResponseHandler.handleResponse(requestMeta,responseMeta,bos);
                        //处理keep-alive
                        if("Close".equalsIgnoreCase(requestMeta.connection)){
                            socket.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    /**处理请求*/
    public void handleRequest(RequestMeta requestMeta,ResponseMeta responseMeta) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException, IOException {
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
        //查找请求路径
        Method method = ControllerUtil.getMethod(requestMeta.requestURI);
        if(method==null){
            responseMeta.response(ResponseMeta.HttpStatus.NOT_FOUND);
            return;
        }
        //判断是否支持该方法
        RequestMapping methodRequestMapping = method.getAnnotation(RequestMapping.class);
        RequestMethod[] requestMethods = methodRequestMapping.method();
        boolean support = false;
        if(requestMethods.length==0){
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
        //解析RequestParam参数
        Object controller = method.getDeclaringClass().newInstance();
        List<Object> parameterList = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        Object result = null;
        if(parameters.length==0){
            result = method.invoke(controller);
        }else{
            for(Parameter parameter:parameters){
                if(parameter.getType().getName().equals("cn.schoolwow.quickserver.request.RequestMeta")){
                    parameterList.add(requestMeta);
                }else if(parameter.getType().getName().equals("cn.schoolwow.quickserver.response.ResponseMeta")){
                    parameterList.add(responseMeta);
                }else{
                    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                    if(requestParam!=null){
                        String requestParameter = requestMeta.parameters.get(requestParam.value());
                        if(requestParam.required()&&requestParameter==null){
                            responseMeta.response(ResponseMeta.HttpStatus.BAD_REQUEST);
                            responseMeta.body = "请求参数["+requestParam.value()+"]不能为空!";
                            return;
                        }
                        if(requestParameter==null){
                            requestParameter = requestParam.defaultValue();
                        }
                        parameterList.add(parameter.getType().getConstructor(String.class).newInstance(requestParameter));
                    }
                }
            }
            result = method.invoke(controller,parameterList.toArray(new Object[]{parameterList.size()}));
        }
        logger.debug("[调用方法]请求路径:{},调用方法名:{},方法参数:{}",requestMeta.method+" "+requestMeta.requestURI,method.getName(),parameterList);
        if(responseMeta.status==0){
            responseMeta.response(ResponseMeta.HttpStatus.OK);
        }
        if(responseMeta.contentType==null){
            responseMeta.contentType = "text/plain; charset="+responseMeta.charset;
        }
        if(responseMeta.body==null){
            responseMeta.body = result.toString();
        }else{
            responseMeta.body += result.toString();
        }
    }
}
