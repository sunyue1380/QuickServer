package cn.schoolwow.quickserver.response;

import cn.schoolwow.quickserver.request.RequestMeta;
import cn.schoolwow.quickserver.util.QuickServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class ResponseHandler {
    private static Logger logger = LoggerFactory.getLogger(ResponseHandler.class);

    /**
     * 根据ResponseMeta写入返回信息
     */
    public static void handleResponse(RequestMeta requestMeta, ResponseMeta responseMeta) throws IOException {
        //处理压缩
        byte[] body = handleAcceptEncoding(requestMeta, responseMeta);
        if (body != null) {
            responseMeta.headers.put("Content-Length", body.length + "");
        }
        StringBuilder result = new StringBuilder();
        result.append(requestMeta.protocol + " " + responseMeta.status + " " + responseMeta.statusMessage + "\r\n");
        for (Map.Entry<String, String> entry : responseMeta.headers.entrySet()) {
            result.append(entry.getKey() + ": " + entry.getValue() + "\r\n");
        }
        List<HttpCookie> httpCookieList = responseMeta.cookies;
        for (HttpCookie httpCookie : httpCookieList) {
            result.append("Set-Cookie: ");
            result.append(httpCookie.getName() + "=" + httpCookie.getValue() + ";");
            if (httpCookie.getMaxAge() > 0) {
                result.append(" Max-Age=" + httpCookie.getMaxAge() + ";");
            }
            if (null != httpCookie.getDomain()) {
                result.append(" Domain=" + httpCookie.getDomain() + ";");
            }
            if (null != httpCookie.getPath()) {
                result.append(" Path=" + httpCookie.getPath() + ";");
            }
            if (httpCookie.getSecure()) {
                result.append(" Secure;");
            }
            if (httpCookie.isHttpOnly()) {
                result.append(" HttpOnly;");
            }
            result.append("\r\n");
        }
        result.append("\r\n");
        logger.trace("[返回头部]返回头部信息:\n{}",result.toString());
        responseMeta.outputStream.write(result.toString().getBytes());

        if (null==body) {
            //不压缩
            writeBody(responseMeta, responseMeta.outputStream);
        } else {
            //压缩
            responseMeta.outputStream.write(body);
        }
        responseMeta.outputStream.flush();
        logger.trace("[返回主体]返回主体内容写入完毕!");
    }

    /**
     * 处理压缩
     */
    private static byte[] handleAcceptEncoding(RequestMeta requestMeta, ResponseMeta responseMeta) throws IOException {
        //文本类资源才需要压缩
        if (QuickServerConfig.compressSupports == null || !responseMeta.contentType.startsWith("text/") || null!=requestMeta.acceptEncoding) {
            return null;
        }
        if (requestMeta.acceptEncoding == null || requestMeta.acceptEncoding.isEmpty()) {
            return null;
        }
        for (String supportCompress : QuickServerConfig.compressSupports) {
            if (requestMeta.acceptEncoding.contains(supportCompress)) {
                responseMeta.headers.put("Content-Encoding", supportCompress);
                break;
            }
        }
        String contentEncoding = responseMeta.headers.get("Content-Encoding");
        //TODO 目前暂时只支持gzip压缩
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GZIPOutputStream gzipOutputStream = new GZIPOutputStream(out);
            writeBody(responseMeta, gzipOutputStream);
            gzipOutputStream.close();
            return out.toByteArray();
        } else {
            return null;
        }
    }

    /**
     * 写入主体内容
     */
    private static void writeBody(ResponseMeta responseMeta, OutputStream outputStream) throws IOException {
        if (null!=responseMeta.body) {
            outputStream.write(responseMeta.body.getBytes(responseMeta.charset));
        }else if(null!=responseMeta.staticURL) {
            InputStream inputStream = responseMeta.inputStream;
            int length = -1;
            byte[] bytes = new byte[8192];
            while ((length = inputStream.read(bytes, 0, bytes.length)) != -1) {
                outputStream.write(bytes, 0, length);
            }
        }
    }
}
