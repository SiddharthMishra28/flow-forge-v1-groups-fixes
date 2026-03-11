package com.ubs.orkestra.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the automation status of a Flow.
 * Indicates the level of automation implemented for a test flow.
 */
public enum AutomationStatus {
    /**
     * Flow is fully automated
     */
    AUTOMATED("Automated"),
    
    /**
     * Flow is partially automated (some manual steps required)
     */
    PARTIAL("Partial"),
    
    /**
     * Flow is not automated (in progress)
     */
    NOT_AUTOMATED("In Progress");

    private final String displayName;

    AutomationStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Convert from string value to enum
     * Supports both display names and enum names for backward compatibility
     */
    @JsonCreator
    public static AutomationStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        
        for (AutomationStatus status : AutomationStatus.values()) {
            if (status.displayName.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        
        throw new IllegalArgumentException("Invalid automation status: '" + value + 
            "'. Allowed values are: 'Automated', 'Partial', 'In Progress'");
    }
}
