package com.ubs.orkestra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import com.ubs.orkestra.validator.ValidInvokeScheduler;
import io.swagger.v3.oas.annotations.media.Schema;

@ValidInvokeScheduler
public class InvokeSchedulerDto {

    @Pattern(regexp = "^(scheduled|delayed)$", message = "Type must be either 'scheduled' or 'delayed'")
    @Schema(description = "Scheduler type - 'scheduled' for absolute time scheduling, 'delayed' for relative delay", 
            example = "scheduled", allowableValues = {"scheduled", "delayed"})
    private String type;

    @Valid
    @Schema(description = "Timer configuration object")
    private TimerDto timer;

    // Constructors
    public InvokeSchedulerDto() {}

    public InvokeSchedulerDto(String type, TimerDto timer) {
        this.type = type;
        this.timer = timer;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public TimerDto getTimer() {
        return timer;
    }

    public void setTimer(TimerDto timer) {
        this.timer = timer;
    }
}