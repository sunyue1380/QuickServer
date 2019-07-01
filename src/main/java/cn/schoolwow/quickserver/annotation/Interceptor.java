package cn.schoolwow.quickserver.annotation;

import cn.schoolwow.quickbeans.annotation.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**拦截器*/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Interceptor {
    /**拦截器路径*/
    String[] patterns();
    /**拦截器排除路径*/
    String[] excludePatterns() default {};
}
