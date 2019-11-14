package cn.schoolwow.quickserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExpUtil {
    private static Logger logger = LoggerFactory.getLogger(RegExpUtil.class);

    /**普通匹配,提取中括号中的内容*/
    public static String plainMatch(String input,String pattern){
        int patternStartIndex = -1,patternEndIndex=-1;
        //找到第一个非转义的(
        for(int i=1;i<pattern.length();i++){
            if(pattern.charAt(0)=='('){
                patternStartIndex = 0;
            }
            if((pattern.charAt(i)=='(')&&(pattern.charAt(i-1)!='\\')){
                patternStartIndex = i;
            }
            if(pattern.charAt(0)==')'){
                patternEndIndex = 0;
            }
            if((pattern.charAt(i)==')')&&(pattern.charAt(i-1)!='\\')){
                patternEndIndex = i;
            }
        }
        if(patternEndIndex<=patternStartIndex){
            logger.warn("[匹配失败]模式中不包含中括号");
            return null;
        }
        String prefix = pattern.substring(0,patternStartIndex).replace("\\(","(").replace("\\)",")");
        String suffix = pattern.substring(patternEndIndex+1).replace("\\(","(").replace("\\)",")");
        int inputStartIndex = input.indexOf(prefix);
        if(inputStartIndex<0){
            return null;
        }
        int inputEndIndex = input.substring(inputStartIndex+prefix.length()).indexOf(suffix);
        if(inputEndIndex<0){
            return null;
        }
        return input.substring(inputStartIndex+prefix.length(),inputStartIndex+inputEndIndex+prefix.length());
    }
}
