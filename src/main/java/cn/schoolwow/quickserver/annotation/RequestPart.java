package cn.schoolwow.quickserver.annotation;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestPart {
    /**参数值*/
    String name();
    /**是否必须*/
    boolean required() default true;
}
