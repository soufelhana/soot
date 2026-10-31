package org.springframework.kafka.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface KafkaListener {
    String[] topics() default {};
    String groupId() default "";
}
