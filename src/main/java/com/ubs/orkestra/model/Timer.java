package com.ubs.orkestra.model;

import jakarta.persistence.*;

@Embeddable
public class Timer {

    @Column(name = "timer_minutes")
    private String minutes;

    @Column(name = "timer_hours")
    private String hours;

    @Column(name = "timer_days")
    private String days;

    // Constructors
    public Timer() {}

    public Timer(String minutes, String hours, String days) {
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