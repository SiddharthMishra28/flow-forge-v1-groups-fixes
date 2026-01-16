package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public class DelayDto {

    @NotNull(message = "Time unit is required")
    @Pattern(regexp = "^(minutes|hours|days)$", message = "Time unit must be one of: minutes, hours, days")
    @Schema(description = "Time unit for delay", allowableValues = {"minutes", "hours", "days"}, example = "minutes")
    private String timeUnit;

    @NotNull(message = "Value is required")
    @Pattern(regexp = "^\\+\\d+$", message = "Value must be a positive number with '+' prefix (e.g., '+10')")
    @Schema(description = "Delay value with '+' prefix", example = "+10")
    private String value;

    // Constructors
    public DelayDto() {}

    public DelayDto(String timeUnit, String value) {
        this.timeUnit = timeUnit;
        this.value = value;
    }

    // Getters and Setters
    public String getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}