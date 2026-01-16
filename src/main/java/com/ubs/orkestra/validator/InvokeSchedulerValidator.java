package com.ubs.orkestra.validator;

import com.ubs.orkestra.dto.InvokeSchedulerDto;
import com.ubs.orkestra.dto.TimerDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class InvokeSchedulerValidator implements ConstraintValidator<ValidInvokeScheduler, InvokeSchedulerDto> {

    @Override
    public void initialize(ValidInvokeScheduler constraintAnnotation) {
    }

    @Override
    public boolean isValid(InvokeSchedulerDto invokeSchedulerDto, ConstraintValidatorContext context) {
        if (invokeSchedulerDto == null) {
            return true; // Null is valid
        }

        String type = invokeSchedulerDto.getType();
        TimerDto timer = invokeSchedulerDto.getTimer();

        // If type is null, then timer should also be null (both required when scheduler is provided)
        if (type == null && timer == null) {
            return true; // Both null is valid for optional scheduler
        }
        
        // If one is provided, both must be provided
        if (type == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Type is required when timer is provided.")
                   .addPropertyNode("type").addConstraintViolation();
            return false;
        }
        
        if (timer == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Timer configuration is required when type is provided.")
                   .addPropertyNode("timer").addConstraintViolation();
            return false;
        }

        boolean hasMinutes = timer.getMinutes() != null && !timer.getMinutes().trim().isEmpty();
        boolean hasHours = timer.getHours() != null && !timer.getHours().trim().isEmpty();
        boolean hasDays = timer.getDays() != null && !timer.getDays().trim().isEmpty();

        // At least one time field must be specified
        if (!hasMinutes && !hasHours && !hasDays) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("At least one of minutes, hours, or days must be specified in timer configuration.")
                   .addPropertyNode("timer.minutes").addConstraintViolation();
            return false;
        }

        // Validate format based on type
        if ("delayed".equals(type)) {
            return validateDelayedFormat(timer, context);
        } else if ("scheduled".equals(type)) {
            return validateScheduledFormat(timer, context);
        }

        return true;
    }

    private boolean validateDelayedFormat(TimerDto timer, ConstraintValidatorContext context) {
        boolean valid = true;

        if (timer.getMinutes() != null && !timer.getMinutes().trim().isEmpty()) {
            if (!timer.getMinutes().matches("^\\+\\d+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("For 'delayed' type, minutes must have '+' prefix and be a positive number (e.g., '+10')")
                       .addPropertyNode("timer.minutes").addConstraintViolation();
                valid = false;
            }
        }

        if (timer.getHours() != null && !timer.getHours().trim().isEmpty()) {
            if (!timer.getHours().matches("^\\+\\d+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("For 'delayed' type, hours must have '+' prefix and be a positive number (e.g., '+2')")
                       .addPropertyNode("timer.hours").addConstraintViolation();
                valid = false;
            }
        }

        if (timer.getDays() != null && !timer.getDays().trim().isEmpty()) {
            if (!timer.getDays().matches("^\\+\\d+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("For 'delayed' type, days must have '+' prefix and be a positive number (e.g., '+1')")
                       .addPropertyNode("timer.days").addConstraintViolation();
                valid = false;
            }
        }

        return valid;
    }

    private boolean validateScheduledFormat(TimerDto timer, ConstraintValidatorContext context) {
        boolean valid = true;

        if (timer.getMinutes() != null && !timer.getMinutes().trim().isEmpty()) {
            if (!timer.getMinutes().matches("^\\d+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("For 'scheduled' type, minutes must be a number without '+' prefix (0-59)")
                       .addPropertyNode("timer.minutes").addConstraintViolation();
                valid = false;
            } else {
                try {
                    int minutes = Integer.parseInt(timer.getMinutes());
                    if (minutes < 0 || minutes > 59) {
                        context.disableDefaultConstraintViolation();
                        context.buildConstraintViolationWithTemplate("For 'scheduled' type, minutes must be between 0 and 59")
                               .addPropertyNode("timer.minutes").addConstraintViolation();
                        valid = false;
                    }
                } catch (NumberFormatException e) {
                    valid = false;
                }
            }
        }

        if (timer.getHours() != null && !timer.getHours().trim().isEmpty()) {
            if (!timer.getHours().matches("^\\d+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("For 'scheduled' type, hours must be a number without '+' prefix (0-23)")
                       .addPropertyNode("timer.hours").addConstraintViolation();
                valid = false;
            } else {
                try {
                    int hours = Integer.parseInt(timer.getHours());
                    if (hours < 0 || hours > 23) {
                        context.disableDefaultConstraintViolation();
                        context.buildConstraintViolationWithTemplate("For 'scheduled' type, hours must be between 0 and 23")
                               .addPropertyNode("timer.hours").addConstraintViolation();
                        valid = false;
                    }
                } catch (NumberFormatException e) {
                    valid = false;
                }
            }
        }

        if (timer.getDays() != null && !timer.getDays().trim().isEmpty()) {
            if (!timer.getDays().matches("^\\d+$")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("For 'scheduled' type, days must be a positive number without '+' prefix")
                       .addPropertyNode("timer.days").addConstraintViolation();
                valid = false;
            } else {
                try {
                    int days = Integer.parseInt(timer.getDays());
                    if (days < 0) {
                        context.disableDefaultConstraintViolation();
                        context.buildConstraintViolationWithTemplate("For 'scheduled' type, days must be a positive number")
                               .addPropertyNode("timer.days").addConstraintViolation();
                        valid = false;
                    }
                } catch (NumberFormatException e) {
                    valid = false;
                }
            }
        }

        return valid;
    }
}