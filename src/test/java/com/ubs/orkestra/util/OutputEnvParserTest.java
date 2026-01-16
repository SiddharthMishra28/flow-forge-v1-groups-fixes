package com.ubs.orkestra.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OutputEnvParserTest {

    private OutputEnvParser parser;

    @BeforeEach
    void setUp() {
        parser = new OutputEnvParser();
    }

    @Test
    void testParseSimpleKeyValue() {
        String content = "KEY1=value1\nKEY2=value2";
        Map<String, String> result = parser.parseOutputEnv(content);
        
        assertEquals(2, result.size());
        assertEquals("value1", result.get("KEY1"));
        assertEquals("value2", result.get("KEY2"));
    }

    @Test
    void testParseWithQuotes() {
        String content = "KEY1=\"quoted value\"\nKEY2='single quoted'";
        Map<String, String> result = parser.parseOutputEnv(content);
        
        assertEquals("quoted value", result.get("KEY1"));
        assertEquals("single quoted", result.get("KEY2"));
    }

    @Test
    void testParseWithExport() {
        String content = "export KEY1=value1\nexport KEY2=\"value2\"";
        Map<String, String> result = parser.parseOutputEnv(content);
        
        assertEquals("value1", result.get("KEY1"));
        assertEquals("value2", result.get("KEY2"));
    }

    @Test
    void testParseWithComments() {
        String content = "# This is a comment\nKEY1=value1\n# Another comment\nKEY2=value2";
        Map<String, String> result = parser.parseOutputEnv(content);
        
        assertEquals(2, result.size());
        assertEquals("value1", result.get("KEY1"));
        assertEquals("value2", result.get("KEY2"));
    }

    @Test
    void testMergeVariables() {
        Map<String, String> existing = new HashMap<>();
        existing.put("KEY1", "old_value");
        existing.put("KEY2", "keep_value");
        
        Map<String, String> newVars = new HashMap<>();
        newVars.put("KEY1", "new_value");
        newVars.put("KEY3", "added_value");
        
        Map<String, String> result = parser.mergeVariables(existing, newVars);
        
        assertEquals(3, result.size());
        assertEquals("new_value", result.get("KEY1")); // Should be overwritten
        assertEquals("keep_value", result.get("KEY2")); // Should be kept
        assertEquals("added_value", result.get("KEY3")); // Should be added
    }

    @Test
    void testVariablesToEnvFormat() {
        Map<String, String> variables = new HashMap<>();
        variables.put("KEY1", "simple_value");
        variables.put("KEY2", "value with spaces");
        variables.put("KEY3", "value\"with\"quotes");
        
        String result = parser.variablesToEnvFormat(variables);
        
        assertTrue(result.contains("KEY1=simple_value"));
        assertTrue(result.contains("KEY2=\"value with spaces\""));
        assertTrue(result.contains("KEY3=\"value\\\"with\\\"quotes\""));
    }
}