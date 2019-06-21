package cn.schoolwow.quickserver.request;

import cn.schoolwow.quickserver.annotation.RequestHeader;
import cn.schoolwow.quickserver.util.IOUtil;
import cn.schoolwow.quickserver.util.RegExpUtil;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.IOUtils;

import java.io.BufferedInputStream;
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
                httpHeaderBuffer.append((char)b);
                //连续两个换行时则停止循环 \r=>0x0D \n=>0x0A
                if(b==CR){
                    byte[] bytes = new byte[3];
                    requestMeta.inputStream.read(bytes);
                    if(bytes[0]==LF&&bytes[1]==CR&&bytes[2]==LF){
                        httpHeaderBuffer.append((char)bytes[0]);
                        break;
                    }else{
                        httpHeaderBuffer.append((char)bytes[0]);
                        httpHeaderBuffer.append((char)bytes[1]);
                        httpHeaderBuffer.append((char)bytes[2]);
                    }
                }
            }
            logger.trace("[读取头部信息]{}",httpHeaderBuffer.toString());
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
        //处理分块传输
        handleTransferEncoding(requestMeta);
        //解析body
        if (requestMeta.contentType != null) {
            if(requestMeta.contentType.contains("multipart/form-data")){
                handleMultipartFormData(requestMeta);
            }else{
                if(requestMeta.contentType.contains("application/x-www-form-urlencoded")
                        ||requestMeta.contentType.startsWith("text/")){
                    int b;
                    StringBuffer bodyBuffer = new StringBuffer();
                    for(int i=0;i<requestMeta.contentLength;i++){
                        b = requestMeta.inputStream.read();
                        bodyBuffer.append((char)b);
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

    /**处理Transfer-Encoding*/
    private static void handleTransferEncoding(RequestMeta requestMeta) throws IOException {
        //分块读取
        if(!requestMeta.headers.containsKey("transfer-encoding")){
            return;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while(true){
            String line = IOUtil.readLine(requestMeta.inputStream);
            int length = Integer.parseInt(line,16);
            if(length==0){
                break;
            }
            byte[] bytes = new byte[length];
            requestMeta.inputStream.read(bytes);
            baos.write(bytes);
            requestMeta.inputStream.read();//CR
            requestMeta.inputStream.read();//LF
        }
        baos.flush();
        byte[] bytes = baos.toByteArray();
        baos.close();
        requestMeta.inputStream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        logger.debug("[处理分块传输]总大小:{}",bytes.length);
    }

    /**处理文件上传*/
    private static void handleMultipartFormData(RequestMeta requestMeta) throws IOException {
        logger.trace("[boundary]boundary:{}",requestMeta.boundary);
        byte[] splitBoundary = ("--"+requestMeta.boundary).getBytes();
        int b;
        //读取第一行
        IOUtil.readLine(requestMeta.inputStream);
        while(true){
            MultipartFile multipartFile= new MultipartFile();
            //Content-Disposition
            {
                String contentDisposition = IOUtil.readLine(requestMeta.inputStream);
                multipartFile.name = RegExpUtil.extract(contentDisposition,"name=\"(?<name>\\w+)\"","name");;
                if(contentDisposition.contains("filename=")){
                    multipartFile.originalFilename = RegExpUtil.extract(contentDisposition,"filename=\"(?<filename>.*)\"$","filename");
                }
            }
            //额外行
            {
                StringBuffer extraHeaderBuffer = new StringBuffer();
                String line = IOUtil.readLine(requestMeta.inputStream);
                while(!line.equals("")){
                    extraHeaderBuffer.append(line);
                    line = IOUtil.readLine(requestMeta.inputStream);
                }
                if(extraHeaderBuffer.length()>0){
                    logger.trace("[额外头部]{}",extraHeaderBuffer.toString());
                    String[] extraHeaders = extraHeaderBuffer.toString().split("\r\n");
                    for(String extraHeader:extraHeaders){
                        if(extraHeader.startsWith("Content-Type")){
                            multipartFile.contentType = extraHeader.substring(extraHeader.indexOf(":")+1);
                            break;
                        }
                    }
                }
            }
            //Body部分
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while((b = requestMeta.inputStream.read())!=-1){
                    if(b==CR){
                        //判断下一个字符是否是回车
                        b = requestMeta.inputStream.read();
                        if(b!=LF){
                            baos.write(CR);
                            baos.write(b);
                            continue;
                        }
                        //判断是否是分隔符
                        byte[] bytes = new byte[splitBoundary.length];
                        requestMeta.inputStream.read(bytes);
                        boolean isBoundaryEnd = true;
                        for(int i=0;i<bytes.length;i++){
                            if(bytes[i]!=splitBoundary[i]){
                                isBoundaryEnd = false;
                                break;
                            }
                        }
                        if(isBoundaryEnd){
                            break;
                        }else{
                            baos.write(CR);
                            baos.write(LF);
                            baos.write(bytes);
                            continue;
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
                    logger.trace("[添加文件参数]字段名:{},原始文件名:{},文件大小:{}",multipartFile.name,multipartFile.originalFilename,multipartFile.bytes.length);
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
