package org.springframework.scheduling.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduled {
    String cron() default "";
    long fixedDelay() default -1;
    String fixedDelayString() default "";
    long fixedRate() default -1;
    String fixedRateString() default "";
}
