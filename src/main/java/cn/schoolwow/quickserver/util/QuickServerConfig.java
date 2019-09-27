package cn.schoolwow.quickserver.util;

public class QuickServerConfig {
    /**
     * 支持压缩格式
     */
    public static String[] compressSupports = new String[]{"gzip"};
    /**
     * 会话Cookie字段
     */
    public static final String SESSION = "quickServerSession";
    /**
     * 真实路径
     */
    public static String realPath;
}
