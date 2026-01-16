package com.ubs.orkestra.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;

@Embeddable
public class InvokeTimer {

    @Pattern(regexp = "^\\+\\d+$", message = "Minutes must be a positive number with '+' prefix (e.g., '+10')")
    @Column(name = "minutes")
    private String minutes;

    @Pattern(regexp = "^\\+\\d+$", message = "Hours must be a positive number with '+' prefix (e.g., '+2')")
    @Column(name = "hours")
    private String hours;

    @Pattern(regexp = "^\\+\\d+$", message = "Days must be a positive number with '+' prefix (e.g., '+1')")
    @Column(name = "days")
    private String days;

    // Constructors
    public InvokeTimer() {}

    public InvokeTimer(String minutes, String hours, String days) {
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