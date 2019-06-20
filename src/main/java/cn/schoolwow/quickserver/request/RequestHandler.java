package cn.schoolwow.quickserver.request;

import cn.schoolwow.quickserver.util.RegExpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler {
    private static Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private static final int CR = 0x0D;
    private static final int LF = 0x0A;

    /**
     * 解析Http请求
     * */
    public static RequestMeta parseRequest(RequestMeta requestMeta) throws IOException {
        //读取头部信息
        {
            StringBuffer httpHeaderBuffer = new StringBuffer();
            int b;
            while((b = requestMeta.inputStream.read())!=-1){
                //连续两个换行时则停止循环 \r=>0x0D \n=>0x0A
                if(b==CR){
                    byte[] bytes = new byte[3];
                    requestMeta.inputStream.read(bytes);
                    httpHeaderBuffer.append(bytes[0]);
                    if(bytes[0]==CR&&bytes[1]==LF&&bytes[2]==CR){
                        break;
                    }
                }
                httpHeaderBuffer.append(b);
            }
            String[] headerLines = httpHeaderBuffer.toString().split("\r\n");
            //处理请求行
            {
                String firstLine = headerLines[0];
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
            {
                //处理get参数
                if (requestMeta.query != null) {
                    handleFormParameter(requestMeta.query,requestMeta);
                }
            }
            //处理header
            {
                for(int i=1;i<headerLines.length;i++){
                    String name = headerLines[i].substring(0, headerLines[i].indexOf(":")).toLowerCase();
                    String value = headerLines[i].substring(headerLines[i].indexOf(":") + 2);
                    requestMeta.headers.put(name, value);
                    if ("content-type".equalsIgnoreCase(name)) {
                        requestMeta.contentType = value;
                        handleContentType(value,requestMeta);
                    }
                    if ("content-length".equalsIgnoreCase(name)) {
                        requestMeta.contentLength = Long.parseLong(value);
                    }
                    if ("Connection".equalsIgnoreCase(name)) {
                        requestMeta.connection = value;
                    }
                }
            }
            //解析cookie
            {
                if(requestMeta.headers.containsKey("cookie")){
                    requestMeta.cookies.putAll(splitParameter(requestMeta.headers.get("cookie")));
                }
            }
        }
        //解析body
        if (requestMeta.contentType != null) {
            if(requestMeta.contentType.contains("multipart/form-data")){
                handleMultipartFormData(requestMeta);
            }else{
                if(requestMeta.contentType.contains("application/x-www-form-urlencoded")
                        ||requestMeta.contentType.startsWith("text/")){
                    int b;
                    StringBuffer bodyBuffer = new StringBuffer();
                    while((b = requestMeta.inputStream.read())!=1){
                        bodyBuffer.append(b);
                    }
                    requestMeta.body = new String(bodyBuffer.toString().getBytes(),requestMeta.charset);
                }
                //处理post表单参数
                if(requestMeta.contentType.contains("application/x-www-form-urlencoded")){
                    handleFormParameter(requestMeta.body,requestMeta);
                }
            }
        }
        logger.debug("[请求元数据]请求行:{}", requestMeta.method + " " + requestMeta.requestURI + " " + requestMeta.protocol);
        return requestMeta;
    }

    /**处理文件上传*/
    private static void handleMultipartFormData(RequestMeta requestMeta) throws IOException {
        logger.debug("[boundary]boundary:{}",requestMeta.boundary);
        byte[] splitBoundary = ("--"+requestMeta.boundary).getBytes();
        int b;
        //读取分隔符
        {
            while((b = requestMeta.inputStream.read())!=-1){
                if(b==CR){
                    b = requestMeta.inputStream.read();
                    if(b==LF){
                        break;
                    }
                }
            }
        }
        while(true){
            MultipartFile multipartFile= new MultipartFile();
            StringBuffer lineBuffer = new StringBuffer();
            //Content-Disposition
            {
                while((b = requestMeta.inputStream.read())!=-1){
                    if(b==CR){
                        b = requestMeta.inputStream.read();
                        if(b==LF){
                            break;
                        }
                    }
                    lineBuffer.append(b);
                }
                String contentDisposition = lineBuffer.toString();
                multipartFile.name = RegExpUtil.extract(contentDisposition,"name=\"(?<name>\\w+)\"","name");;
                if(contentDisposition.contains("filename=")){
                    multipartFile.originalFilename = RegExpUtil.extract(contentDisposition,"filename=\"(?<filename>.*)\"$","filename");
                }
            }
            //额外行
            {
                lineBuffer.setLength(0);
                while((b = requestMeta.inputStream.read())!=-1){
                    lineBuffer.append(b);
                    //连续两个换行时则停止循环 \r=>0x0D \n=>0x0A
                    if(b==CR){
                        byte[] bytes = new byte[3];
                        requestMeta.inputStream.read(bytes);
                        lineBuffer.append(bytes[0]);
                        if(bytes[0]==CR&&bytes[1]==LF&&bytes[2]==CR){
                            break;
                        }
                    }
                }
                String[] extraHeaders = lineBuffer.toString().split("\r\n");
                for(String extraHeader:extraHeaders){
                    if(extraHeader.startsWith("Content-Type")){
                        multipartFile.contentType = extraHeader.substring(extraHeader.indexOf(":")+1);
                        break;
                    }
                }
            }
            //Body部分
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while((b = requestMeta.inputStream.read())!=-1){
                    if(b==splitBoundary[0]){
                        byte[] bytes = new byte[splitBoundary.length-1];
                        requestMeta.inputStream.read(bytes);
                        boolean isBoundaryEnd = true;
                        for(int i=0;i<bytes.length;i++){
                            if(bytes[i]!=splitBoundary[i+1]){
                                isBoundaryEnd = false;
                                break;
                            }
                        }
                        if(isBoundaryEnd){
                            break;
                        }
                    }
                    baos.write(b);
                }
                baos.flush();
                multipartFile.bytes = baos.toByteArray();
                if(multipartFile.originalFilename==null){
                    requestMeta.parameters.put(multipartFile.name,new String(multipartFile.bytes,requestMeta.charset));
                }else{
                    multipartFile.inputStream = new ByteArrayInputStream(multipartFile.bytes);
                    multipartFile.size = multipartFile.bytes.length;
                    multipartFile.isEmpty = multipartFile.bytes.length==0;
                    requestMeta.fileParameters.put(multipartFile.name,multipartFile);
                    logger.debug("[添加文件参数]{}:{},文件大小:{},",multipartFile.name,multipartFile,multipartFile.bytes.length);
                }
                baos.close();
            }
            //判断是否是末尾还是换行
            {
                byte[] bytes = new byte[2];
                requestMeta.inputStream.read(bytes);
                if(bytes[0]==45&&bytes[1]==45){
                    break;
                }
            }
        }
    }

    /**处理Content-Type*/
    private static void handleContentType(String contentType,RequestMeta requestMeta){
        int startIndex = contentType.indexOf(";");
        if(startIndex<0){
            return;
        }
        Map<String,String> map = splitParameter(contentType.substring(startIndex+1));
        if(map.containsKey("boundary")){
            requestMeta.boundary = map.get("boundary");
        }
        if(map.containsKey("charset")){
            requestMeta.charset = map.get("charset");
        }
    }

    /**解析cookie和contentType*/
    public static Map<String,String> splitParameter(String s){
        Map<String,String> map = new HashMap<>();
        String[] tokens = s.split(";");
        for(String token:tokens){
            int startIndex = token.indexOf("=");
            String name = token.substring(0,startIndex).trim();
            String value = token.substring(startIndex+1).trim();
            map.put(name,value);
        }
        return map;
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
