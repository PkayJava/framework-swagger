package com.angkorteam.framework.swagger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Adds additional meta-data for operation parameters.
 * <p>
 * This annotation can be used only in combination of JAX-RS 1.x/2.x annotations.
 */
@Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiProperty {

    String description() default "";

    Class<?> model() default Void.class;

    boolean array() default false;

    boolean date() default false;

    boolean dateTime() default false;

    boolean password() default false;

    boolean email() default false;

    boolean uri() default false;

    boolean url() default false;
}
