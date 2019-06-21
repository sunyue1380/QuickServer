package cn.schoolwow.quickserver.util;

import java.io.IOException;
import java.io.InputStream;

public class IOUtil {
    private static final int CR = 0x0D;
    private static final int LF = 0x0A;

    /**读取一行*/
    public static String readLine(InputStream inputStream) throws IOException {
        int b;
        StringBuffer lineBuffer = new StringBuffer();
        while((b = inputStream.read())!=-1){
            if(b==CR){
                //遇到换行时停止
                b = inputStream.read();
                if(b==LF){
                    break;
                }else{
                    lineBuffer.append((char)CR);
                    lineBuffer.append((char)b);
                    continue;
                }
            }
            lineBuffer.append((char)b);
        }
        return lineBuffer.toString();
    }
}
