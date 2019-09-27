package cn.schoolwow.quickserver.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExpUtil {
    /**
     * 保存Pattern对象
     */
    private static Map<String, Pattern> patternCache = new HashMap<>();

    /**
     * 正则提取
     *
     * @param input       输入字符串
     * @param pattern     正则表达式
     * @param groupNumber 分组序号
     */
    public static String extract(String input, String pattern, int groupNumber) {
        if (!patternCache.containsKey(pattern)) {
            patternCache.put(pattern, Pattern.compile(pattern));
        }
        Pattern p = patternCache.get(pattern);
        Matcher matcher = p.matcher(input);
        if (matcher.find()) {
            return matcher.group(groupNumber);
        }
        return null;
    }

    /**
     * 正则提取
     *
     * @param input     输入字符串
     * @param pattern   正则表达式
     * @param groupName 分组名
     */
    public static String extract(String input, String pattern, String groupName) {
        if (!patternCache.containsKey(pattern)) {
            patternCache.put(pattern, Pattern.compile(pattern));
        }
        Pattern p = patternCache.get(pattern);
        Matcher matcher = p.matcher(input);
        if (matcher.find()) {
            return matcher.group(groupName);
        }
        return null;
    }
}
