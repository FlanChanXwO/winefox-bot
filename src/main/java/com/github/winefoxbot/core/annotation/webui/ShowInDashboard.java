package com.github.winefoxbot.core.annotation.webui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShowInDashboard {
    String label();
    String description() default "";
    int order() default 0;
}