package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotBlank;

public class FlowExecutionRequestDto {

    @NotBlank(message = "Category cannot be blank")
    private String category;

    public FlowExecutionRequestDto() {}

    public FlowExecutionRequestDto(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}