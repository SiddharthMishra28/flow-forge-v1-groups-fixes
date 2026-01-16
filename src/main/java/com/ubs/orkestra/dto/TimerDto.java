package com.ubs.orkestra.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class TimerDto {

    @Schema(description = "Minutes - for 'scheduled' type: absolute minute (0-59), for 'delayed' type: relative minutes with '+' prefix", 
            example = "30")
    private String minutes;

    @Schema(description = "Hours - for 'scheduled' type: absolute hour (0-23), for 'delayed' type: relative hours with '+' prefix", 
            example = "14")
    private String hours;

    @Schema(description = "Days - for 'scheduled' type: days from now (absolute), for 'delayed' type: relative days with '+' prefix", 
            example = "1")
    private String days;

    // Constructors
    public TimerDto() {}

    public TimerDto(String minutes, String hours, String days) {
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