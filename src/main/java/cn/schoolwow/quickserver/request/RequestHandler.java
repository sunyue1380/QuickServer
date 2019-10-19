package cn.schoolwow.quickserver.request;

import cn.schoolwow.quickserver.util.IOUtil;
import cn.schoolwow.quickserver.util.RegExpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler {
    private static Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private static final int CR = 0x0D;
    private static final int LF = 0x0A;

    /**
     * 解析Http请求
     * @return 是否成功解析Http请求报文
     */
    public static boolean parseRequest(RequestMeta requestMeta) throws IOException {
        logger.trace("[开始解析Http请求]");
        //读取头部信息
        {
            StringBuffer httpHeaderBuffer = new StringBuffer();
            int b;
            while ((b = requestMeta.inputStream.read()) != -1) {
                httpHeaderBuffer.append((char) b);
                //连续两个换行时则停止循环 \r=>0x0D \n=>0x0A
                if (b == CR) {
                    byte[] bytes = new byte[3];
                    requestMeta.inputStream.read(bytes);
                    if (bytes[0] == LF && bytes[1] == CR && bytes[2] == LF) {
                        httpHeaderBuffer.append((char) bytes[0]);
                        break;
                    } else {
                        httpHeaderBuffer.append((char) bytes[0]);
                        httpHeaderBuffer.append((char) bytes[1]);
                        httpHeaderBuffer.append((char) bytes[2]);
                    }
                }
            }
            logger.trace("[读取头部信息]头部字段\n{}", httpHeaderBuffer.toString());
            if (httpHeaderBuffer.toString().trim().isEmpty()) {
                return false;
            }
            String[] headerLines = httpHeaderBuffer.toString().split("\r\n");
            //处理请求行
            {
                String firstLine = headerLines[0];
                if (!firstLine.contains(" ")) {
                    logger.trace("[请求行解析失败]请求行:{}",firstLine);
                    return false;
                }
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
                    handleFormParameter(requestMeta.query, requestMeta);
                }
            }
            //处理header
            {
                for (int i = 1; i < headerLines.length; i++) {
                    if (!headerLines[i].contains(":")) {
                        logger.warn("[头部字段不包含冒号]当前头部字段信息:{}", headerLines[i]);
                        continue;
                    }
                    String name = headerLines[i].substring(0, headerLines[i].indexOf(":"));
                    String value = headerLines[i].substring(headerLines[i].indexOf(":") + 2);
                    requestMeta.headers.put(name, value);
                    switch (name.toLowerCase()) {
                        case "content-type": {
                            requestMeta.contentType = value;
                            handleContentType(value, requestMeta);
                        }
                        break;
                        case "content-length": {
                            requestMeta.contentLength = Long.parseLong(value);
                        }
                        break;
                        case "connection": {
                            requestMeta.connection = value;
                        }
                        break;
                        case "origin": {
                            requestMeta.origin = value;
                        }
                        break;
                        case "authorization": {
                            requestMeta.authorization = value;
                        }
                        break;
                        case "accept-encoding": {
                            requestMeta.acceptEncoding = value;
                        }
                        break;
                        case "transfer-encoding": {
                            requestMeta.transformEncoding = value;
                        }
                        break;
                        case "cookie": {
                            requestMeta.cookies.putAll(splitParameter(value));
                        }
                        break;
                        case "access-control-request-method": {
                            requestMeta.accessControlRequestMethod = value;
                        }
                        break;
                        case "access-control-request-headers": {
                            requestMeta.accessControlRequestHeaders = value;
                        }
                        break;
                    }
                }
            }
        }
        //处理分块传输
        {
            handleTransferEncoding(requestMeta);
        }
        //解析body
        if (requestMeta.contentType != null) {
            if (requestMeta.contentType.contains("multipart/form-data")) {
                handleMultipartFormData(requestMeta);
            } else {
                if (requestMeta.contentType.contains("application/x-www-form-urlencoded")
                        || requestMeta.contentType.contains("application/json")
                        || requestMeta.contentType.startsWith("text/")) {
                    byte[] bytes = new byte[(int) requestMeta.contentLength];
                    requestMeta.inputStream.read(bytes, 0, bytes.length);
                    requestMeta.body = new String(bytes, requestMeta.charset);
                }
                //处理post表单参数
                if (requestMeta.contentType.contains("application/x-www-form-urlencoded")) {
                    handleFormParameter(requestMeta.body, requestMeta);
                }
            }
        }
        logger.trace("[请求行]{}", requestMeta.method + " " + requestMeta.requestURI);
        return true;
    }

    /**
     * 处理Transfer-Encoding
     */
    private static void handleTransferEncoding(RequestMeta requestMeta) throws IOException {
        //分块读取
        if (null == requestMeta.transformEncoding) {
            return;
        }
        logger.trace("[处理分段传输]");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            String line = IOUtil.readLine(requestMeta);
            logger.trace("[读取一行]行内容:{}", line);
            int length = Integer.parseInt(line, 16);
            if (length == 0) {
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
        logger.trace("[处理分块传输]总大小:{}", bytes.length);
    }

    /**
     * 处理文件上传
     */
    private static void handleMultipartFormData(RequestMeta requestMeta) throws IOException {
        logger.trace("[boundary]boundary:{}", requestMeta.boundary);
        byte[] splitBoundary = ("\r\n--" + requestMeta.boundary).getBytes();
        int b;
        //读取第一行
        IOUtil.readLine(requestMeta);
        while (true) {
            MultipartFile multipartFile = new MultipartFile();
            //Content-Disposition
            {
                String contentDisposition = IOUtil.readLine(requestMeta);
                multipartFile.name = RegExpUtil.plainMatch(contentDisposition, "name=\"()\"");
                if(null==multipartFile.name){
                    multipartFile.name = RegExpUtil.plainMatch(contentDisposition, "name=();");
                }
                if(null==multipartFile.name||multipartFile.name.isEmpty()){
                    logger.trace("[文件字段读取失败]当前行:{}",contentDisposition);
                }
                if (contentDisposition.contains("filename=")) {
                    multipartFile.originalFilename = RegExpUtil.plainMatch(contentDisposition, "filename=\"()\"");
                }
            }
            //额外行
            {
                StringBuffer extraHeaderBuffer = new StringBuffer();
                String line = IOUtil.readLine(requestMeta);
                while (!line.equals("")) {
                    extraHeaderBuffer.append(line);
                    line = IOUtil.readLine(requestMeta);
                }
                if (extraHeaderBuffer.length() > 0) {
                    logger.trace("[额外头部]{}", extraHeaderBuffer.toString());
                    String[] extraHeaders = extraHeaderBuffer.toString().split("\r\n");
                    for (String extraHeader : extraHeaders) {
                        if (extraHeader.startsWith("Content-Type")) {
                            multipartFile.contentType = extraHeader.substring(extraHeader.indexOf(":") + 1).trim();
                            break;
                        }
                    }
                }
            }
            //Body部分
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] bytes = new byte[splitBoundary.length];
                requestMeta.inputStream.mark(splitBoundary.length+1);
                requestMeta.inputStream.read(bytes);
                while(requestMeta.inputStream.available()>0&&!Arrays.equals(bytes,splitBoundary)){
                    baos.write(bytes[0]);
                    requestMeta.inputStream.reset();
                    requestMeta.inputStream.read();
                    requestMeta.inputStream.mark(splitBoundary.length+1);
                    requestMeta.inputStream.read(bytes);
                }
                baos.flush();
                multipartFile.bytes = baos.toByteArray();
                if (multipartFile.originalFilename == null) {
                    String value = new String(multipartFile.bytes, requestMeta.charset);
                    requestMeta.parameters.put(multipartFile.name, value);
                    logger.trace("[添加字符串参数]字段名:{},字段值:{}",multipartFile.name,value);
                } else {
                    multipartFile.inputStream = new ByteArrayInputStream(multipartFile.bytes);
                    multipartFile.size = multipartFile.bytes.length;
                    multipartFile.isEmpty = multipartFile.bytes.length == 0;
                    requestMeta.fileParameters.put(multipartFile.name, multipartFile);
                    logger.trace("[添加文件参数]字段名:{},原始文件名:{},文件大小:{}", multipartFile.name, multipartFile.originalFilename, multipartFile.bytes.length);
                }
                baos.close();
            }
            //判断是否是末尾还是换行
            {
                byte[] bytes = new byte[2];
                requestMeta.inputStream.read(bytes);
                if (bytes[0] == 45 && bytes[1] == 45) {
                    //如果是 -- ,则表示文件部分读取完毕
                    break;
                }else{
                    //按理来说应该是读取了换行
                }
            }
        }
    }

    /**
     * 处理Content-Type
     */
    private static void handleContentType(String contentType, RequestMeta requestMeta) {
        int startIndex = contentType.indexOf(";");
        if (startIndex < 0) {
            return;
        }
        Map<String, String> map = splitParameter(contentType.substring(startIndex + 1));
        if (map.containsKey("boundary")) {
            requestMeta.boundary = map.get("boundary");
        }
        if (map.containsKey("charset")) {
            requestMeta.charset = map.get("charset");
        }
    }

    /**
     * 解析cookie和contentType
     */
    public static Map<String, String> splitParameter(String s) {
        Map<String, String> map = new HashMap<>();
        String[] tokens = s.split(";");
        for (String token : tokens) {
            int startIndex = token.indexOf("=");
            String name = token.substring(0, startIndex).trim();
            String value = token.substring(startIndex + 1).trim();
            map.put(name, value);
        }
        return map;
    }


    /**
     * 处理表单参数
     */
    private static void handleFormParameter(String parameter, RequestMeta requestMeta) throws UnsupportedEncodingException {
        String[] tokens = parameter.split("&");
        for (String token : tokens) {
            if (!token.contains("=")) {
                requestMeta.parameters.put(token, "");
            } else {
                String[] _tokens = token.split("=");
                if(_tokens.length<2){
                    requestMeta.parameters.put(_tokens[0], "");
                }else{
                    requestMeta.parameters.put(_tokens[0], URLDecoder.decode(_tokens[1],requestMeta.charset));
                }
            }
        }
    }
}
