package cn.schoolwow.quickserver.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;

public class RequestHandler {
    private static Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    /**
     * 解析Http请求
     * */
    public static RequestMeta parseRequest(BufferedReader br) throws IOException {
        RequestMeta requestMeta = new RequestMeta();
        //读取requestLine
        {
            String firstLine = br.readLine();
            requestMeta.method = firstLine.substring(0, firstLine.indexOf(" ")).toUpperCase();
            firstLine = firstLine.substring(firstLine.indexOf(" ") + 1);
            String requestURI = firstLine.substring(0, firstLine.indexOf(" "));
            if (requestURI.contains("?")) {
                requestMeta.requestURI = requestURI.substring(0, requestURI.indexOf("?"));
                requestMeta.query = requestURI.substring(requestURI.indexOf("?") + 1);
            } else {
                requestMeta.requestURI = requestURI;
            }
            firstLine = firstLine.substring(firstLine.indexOf(" ") + 1);
            requestMeta.protocol = firstLine;
        }
        //处理get参数
        {
            if (requestMeta.query != null) {
                handleFormParameter(requestMeta.query,requestMeta);
            }
        }
        //读取header
        {
            String line = br.readLine();
            while (!line.equals("")) {
                String name = line.substring(0, line.indexOf(":")).toLowerCase();
                String value = line.substring(line.indexOf(":") + 2);
                requestMeta.headers.put(name, value);
                if ("content-type".equalsIgnoreCase(name)) {
                    requestMeta.contentType = value;
                }
                if ("content-length".equalsIgnoreCase(name)) {
                    requestMeta.contentLength = Long.parseLong(value);
                }
                if ("Connection".equalsIgnoreCase(name)) {
                    requestMeta.connection = value;
                }
                line = br.readLine();
            }
        }
        //解析cookie
        {
            String cookie = requestMeta.headers.get("cookie");
            if(cookie!=null){
                String[] tokens = cookie.split(";");
                for(String token:tokens){
                    int startIndex = token.indexOf("=");
                    String name = token.substring(0,startIndex).trim();
                    String value = token.substring(startIndex+1).trim();
                    requestMeta.cookies.put(name,value);
                }
            }
        }
        //解析body
        if (requestMeta.contentType != null) {
            if ("application/x-www-form-urlencoded".equalsIgnoreCase(requestMeta.contentType)
                    || requestMeta.contentType.startsWith("text/")) {
                char[] chars = new char[(int) requestMeta.contentLength];
                int length = br.read(chars,0,chars.length);
                requestMeta.body =new String(chars,0,length);
            }
        }
        //处理post表单参数
        if("application/x-www-form-urlencoded".equalsIgnoreCase(requestMeta.contentType)){
            handleFormParameter(requestMeta.body,requestMeta);
        }
        logger.debug("[请求元数据]请求行:{}", requestMeta.method + " " + requestMeta.requestURI + " " + requestMeta.protocol);
        return requestMeta;
    }

    /**处理表单参数*/
    private static void handleFormParameter(String parameter,RequestMeta requestMeta){
        String[] tokens = parameter.split("&");
        for (String token : tokens) {
            if (!token.contains("=")) {
                requestMeta.parameters.put(token, "");
            } else {
                String[] _tokens = token.split("=");
                requestMeta.parameters.put(_tokens[0], _tokens[1]);
            }
        }
    }
}
