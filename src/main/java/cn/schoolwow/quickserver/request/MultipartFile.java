package cn.schoolwow.quickserver.request;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MultipartFile {
    /**字段名*/
    public String name;

    /**原始文件名*/
    public String originalFilename;

    /**类型*/
    public String contentType;

    /**文件是否为空*/
    public boolean isEmpty;

    /**文件大小*/
    public long size;

    /**字节数组*/
    public byte[] bytes;

    /**输入流*/
    public InputStream inputStream;

    /**传输文件*/
    public void transferTo(File dest) {
        if(!dest.getParentFile().exists()){
            dest.getParentFile().mkdirs();
        }
        try {
            FileOutputStream fos = new FileOutputStream(dest);
            fos.write(bytes,0,bytes.length);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
