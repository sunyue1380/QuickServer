package cn.schoolwow.quickserver.util;

import cn.schoolwow.quickserver.domain.Filter;

/**
 * 路径匹配工具类
 */
public class AntPatternUtil {
    /**
     * 是否匹配拦截器
     *
     * @param url    请求路径
     * @param filter 拦截器
     */
    public static boolean matchFilter(String url, Filter filter) {
        //判断是否匹配排除路径
        for (String excludePattern : filter.excludePatterns) {
            if (doMatch(url, excludePattern)) {
                return false;
            }
        }
        for (String pattern : filter.patterns) {
            if (doMatch(url, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否匹配路径
     *
     * @param url     请求路径
     * @param pattern 拦截器路径
     */
    public static boolean doMatch(String url, String pattern) {
        int urlPos = 0, pattPos = 0;
        char[] urlChars = url.toCharArray(), patternChars = pattern.toCharArray();
        String lastMatch = "";
        while (urlPos < url.length() && pattPos < pattern.length()) {
            if (isNextMatch(pattPos, patternChars, "**/")) {
                pattPos += 3;
                lastMatch = "**/";
            } else if (isNextMatch(pattPos, patternChars, "*")) {
                pattPos += 1;
                lastMatch = "*";
            } else if (isNextMatch(pattPos, patternChars, "?")) {
                pattPos += 1;
                lastMatch = "?";
            } else {
                String nextword = getNextWord(pattPos, patternChars);
                switch (lastMatch) {
                    case "**/": {
                        while (urlPos < url.length() && !isNextMatch(urlPos, urlChars, nextword)) {
                            urlPos++;
                        }
                        urlPos += nextword.length();
                    }
                    break;
                    case "*": {
                        while (urlPos < url.length() && url.charAt(urlPos) != '/' && !isNextMatch(urlPos, urlChars, nextword)) {
                            urlPos++;
                        }
                        if (urlPos < url.length()) {
                            urlPos += nextword.length();
                        }
                    }
                    break;
                    case "?": {
                        urlPos++;
                    }
                    break;
                    default: {
                        if (isNextMatch(urlPos, urlChars, nextword)) {
                            urlPos += nextword.length();
                        } else {
                            return false;
                        }
                    }
                    break;
                }
                pattPos += nextword.length();
            }
        }
        return pattPos == pattern.length();
    }

    /**
     * 获取下一个匹配的字符串
     */
    private static String getNextWord(int start, char[] chars) {
        //获取下一个非匹配字符串
        int end = start + 1;
        while (end < chars.length) {
            if (isNextMatch(end, chars, "**/")
                    || isNextMatch(end, chars, "*")
                    || isNextMatch(end, chars, "?")) {
                break;
            } else {
                end++;
            }
        }
        return new String(chars, start, end - start);
    }

    /**
     * 下一个字符串是否等于key
     */
    private static boolean isNextMatch(int pos, char[] chars, String key) {
        int keyIndex = 0;
        while (pos < chars.length && keyIndex < key.length() && chars[pos] == key.charAt(keyIndex)) {
            pos++;
            keyIndex++;
        }
        if (keyIndex == key.length()) {
            return true;
        } else {
            return false;
        }
    }
}
