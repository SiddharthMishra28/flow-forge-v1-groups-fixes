package com.ubs.orkestra.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Embeddable
public class Delay {

    @NotNull
    @Pattern(regexp = "^(minutes|hours|days)$", message = "Time unit must be one of: minutes, hours, days")
    @Column(name = "time_unit")
    private String timeUnit;

    @NotNull
    @Pattern(regexp = "^\\+\\d+$", message = "Value must be a positive number with '+' prefix (e.g., '+10')")
    @Column(name = "delay_value")
    private String value;

    // Constructors
    public Delay() {}

    public Delay(String timeUnit, String value) {
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