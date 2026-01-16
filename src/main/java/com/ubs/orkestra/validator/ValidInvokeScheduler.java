package com.ubs.orkestra.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = InvokeSchedulerValidator.class)
@Target({ ElementType.TYPE, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidInvokeScheduler {
    String message() default "Invalid InvokeScheduler configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}