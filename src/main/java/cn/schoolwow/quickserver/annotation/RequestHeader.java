package cn.schoolwow.quickserver.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestHeader {
    /**
     * 参数值
     */
    String name();

    /**
     * 是否必须
     */
    boolean required() default true;

    /**
     * 参数默认值
     */
    String defaultValue() default "";
}
