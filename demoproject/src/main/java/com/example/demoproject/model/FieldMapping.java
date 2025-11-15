package com.example.demoproject.model;

public class FieldMapping {
    private String source;   // JSON path: e.g., "user.name"
    private String target;   // PDF field name
    private Object transform; // String or Map (for parametrized)
    private Condition condition;   // ← new
    private String defaultValue;   // ← new: fallback if value is null/empty

    // Getters
    public String getSource() { return source; }
    public String getTarget() { return target; }
    public Object getTransform() { return transform; }
    public Condition getCondition() { return condition; }
    public String getDefaultValue() { return defaultValue; }

     // Setters
    public void setSource(String source) { this.source=source; }
    public void setTarget(String target) { this.target=target; }
    public void setTransform(Object transform) { this.transform=transform; }
    public void setCondition(Condition condition) { this.condition=condition; }
    public void setDefaultValue(String defaultValue) { this.defaultValue=defaultValue; }
    
    
}
