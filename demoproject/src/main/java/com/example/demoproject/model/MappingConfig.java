package com.example.demoproject.model;

import java.util.List;

public class MappingConfig {
    private List<FieldMapping> mappings;

    public List<FieldMapping> getMappings() { return mappings; }
    public void setMappings(List<FieldMapping> mappings) { this.mappings = mappings; }
    

    // toString for debugging
    @Override
    public String toString() {
        return "MappingConfig{" +
                "mappings=" + mappings +
                '}';    
    }
}