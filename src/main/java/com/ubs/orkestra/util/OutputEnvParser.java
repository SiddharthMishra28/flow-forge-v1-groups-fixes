package com.ubs.orkestra.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Component
public class OutputEnvParser {

    private static final Logger logger = LoggerFactory.getLogger(OutputEnvParser.class);

    /**
     * Parse output.env content into a Map of key-value pairs
     * Supports various formats:
     * - KEY=value
     * - KEY="value"
     * - KEY='value'
     * - export KEY=value
     */
    public Map<String, String> parseOutputEnv(String content) {
        Map<String, String> variables = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Output.env content is empty or null");
            return variables;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Remove 'export ' prefix if present
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
                // Find the first '=' character
                int equalIndex = line.indexOf('=');
                if (equalIndex == -1) {
                    logger.warn("Invalid line format at line {}: {}", lineNumber, line);
                    continue;
                }
                
                String key = line.substring(0, equalIndex).trim();
                String value = line.substring(equalIndex + 1).trim();
                
                // Remove quotes if present
                value = removeQuotes(value);
                
                if (!key.isEmpty()) {
                    variables.put(key, value);
                    logger.debug("Parsed variable: {}={}", key, value);
                } else {
                    logger.warn("Empty key at line {}: {}", lineNumber, line);
                }
            }
            
            logger.info("Successfully parsed {} variables from output.env", variables.size());
            
        } catch (Exception e) {
            logger.error("Error parsing output.env content: {}", e.getMessage(), e);
        }
        
        return variables;
    }

    /**
     * Merge runtime variables with existing variables
     * Runtime variables take precedence over existing ones
     */
    public Map<String, String> mergeVariables(Map<String, String> existingVariables, 
                                             Map<String, String> newVariables) {
        Map<String, String> merged = new HashMap<>();
        
        if (existingVariables != null) {
            merged.putAll(existingVariables);
        }
        
        if (newVariables != null) {
            merged.putAll(newVariables);
            logger.debug("Merged {} new variables with {} existing variables", 
                        newVariables.size(), existingVariables != null ? existingVariables.size() : 0);
        }
        
        return merged;
    }

    /**
     * Convert variables map to environment variable format
     */
    public String variablesToEnvFormat(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        variables.forEach((key, value) -> {
            sb.append(key).append("=").append(escapeValue(value)).append("\n");
        });
        
        return sb.toString();
    }

    /**
     * Remove surrounding quotes from a value
     */
    private String removeQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Escape value for environment variable format
     */
    private String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        
        // If value contains spaces or special characters, wrap in quotes
        if (value.contains(" ") || value.contains("\t") || value.contains("\n") || 
            value.contains("\"") || value.contains("'")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        
        return value;
    }
}