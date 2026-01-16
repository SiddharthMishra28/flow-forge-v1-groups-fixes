package com.ubs.orkestra.dto;

import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

public class InvokeTimerDto {

    @Pattern(regexp = "^\\+\\d+$", message = "Minutes must be a positive number with '+' prefix (e.g., '+10')")
    @Schema(description = "Minutes to wait before executing this step", example = "+10")
    private String minutes;

    @Pattern(regexp = "^\\+\\d+$", message = "Hours must be a positive number with '+' prefix (e.g., '+2')")
    @Schema(description = "Hours to wait before executing this step", example = "+2")
    private String hours;

    @Pattern(regexp = "^\\+\\d+$", message = "Days must be a positive number with '+' prefix (e.g., '+1')")
    @Schema(description = "Days to wait before executing this step", example = "+1")
    private String days;

    // Constructors
    public InvokeTimerDto() {}

    public InvokeTimerDto(String minutes, String hours, String days) {
        this.minutes = minutes;
        this.hours = hours;
        this.days = days;
    }

    // Getters and Setters
    public String getMinutes() {
        return minutes;
    }

    public void setMinutes(String minutes) {
        this.minutes = minutes;
    }

    public String getHours() {
        return hours;
    }

    public void setHours(String hours) {
        this.hours = hours;
    }

    public String getDays() {
        return days;
    }

    public void setDays(String days) {
        this.days = days;
    }
}