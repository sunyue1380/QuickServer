package cn.schoolwow.quickserver.util;

import cn.schoolwow.quickserver.request.RequestMeta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class IOUtil {
    private static final int CR = 0x0D;
    private static final int LF = 0x0A;

    /**
     * 读取一行
     */
    public static byte[] readLineByte(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = inputStream.read()) != -1) {
            if (b == CR) {
                //遇到换行时停止
                b = inputStream.read();
                if (b == LF) {
                    break;
                } else {
                    baos.write(CR);
                    baos.write(b);
                    continue;
                }
            }
            baos.write(b);
        }
        baos.flush();
        byte[] bytes = baos.toByteArray();
        baos.close();
        return bytes;
    }

    /**
     * 读取一行
     */
    public static String readLine(RequestMeta requestMeta) throws IOException {
        byte[] bytes = readLineByte(requestMeta.inputStream);
        return Charset.forName(requestMeta.charset).decode(ByteBuffer.wrap(bytes)).toString();
    }
}
