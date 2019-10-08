package cn.schoolwow.quickserver.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跨域请求
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossOrigin {
    /**
     * 允许的域名
     */
    String[] origins() default {};

    /**
     * 预检请求缓存时间
     */
    int maxAge() default 0;

    /**
     * 允许的方法
     */
    String[] methods() default {};

    /**
     * 允许的头部
     */
    String[] headers() default {"*"};

    /**
     * 是否允许发送Cookie
     */
    boolean allowCredentials() default true;

    /**
     * 暴露的头部
     */
    String[] exposedHeaders() default {};
}
