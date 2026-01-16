package com.ubs.orkestra.validator;

import com.ubs.orkestra.dto.InvokeTimerDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class InvokeTimerValidator implements ConstraintValidator<ValidInvokeTimer, InvokeTimerDto> {

    @Override
    public void initialize(ValidInvokeTimer constraintAnnotation) {
    }

    @Override
    public boolean isValid(InvokeTimerDto invokeTimerDto, ConstraintValidatorContext context) {
        if (invokeTimerDto == null) {
            return true; // Null is valid
        }

        boolean hasMinutes = invokeTimerDto.getMinutes() != null && !invokeTimerDto.getMinutes().isBlank();
        boolean hasHours = invokeTimerDto.getHours() != null && !invokeTimerDto.getHours().isBlank();
        boolean hasDays = invokeTimerDto.getDays() != null && !invokeTimerDto.getDays().isBlank();

        // At least one time field must be specified
        if (!hasMinutes && !hasHours && !hasDays) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("At least one of minutes, hours, or days must be specified when invokeTimer is provided.")
                   .addPropertyNode("minutes").addConstraintViolation();
            return false;
        }

        return true;
    }
}
