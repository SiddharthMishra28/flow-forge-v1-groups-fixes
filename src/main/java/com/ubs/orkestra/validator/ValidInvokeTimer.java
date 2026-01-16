package com.ubs.orkestra.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = InvokeTimerValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidInvokeTimer {
    String message() default "Invalid InvokeTimer configuration";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
