package com.angkorteam.framework.swagger;

import org.springframework.http.HttpStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by socheat on 11/26/15.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiResponse {

    HttpStatus httpStatus();

    String description() default "";

    Class<?> response() default Void.class;

    boolean array() default false;

    boolean date() default false;

    boolean dateTime() default false;

    boolean password() default false;

    boolean email() default false;

    boolean uri() default false;

    boolean url() default false;
}
