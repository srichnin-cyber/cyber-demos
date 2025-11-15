package com.example.demoproject.model;

import java.util.List;

public class Condition {
    private String type;           // e.g., "notNull", "equals", "greaterThan", "envVar"
    private String field;          // optional: another JSON path to compare
    private Object value;          // expected value (for equals, greaterThan, etc.)
    private String name;           // for envVar/flag
    private String expected;       // alias for 'value' in env context (more readable)
    private List<Condition> and;   // for compound: and: [cond1, cond2]
    private List<Condition> or;    // for compound: or: [cond1, cond2]

    // Helpers
    public Object getEffectiveValue() {
        return expected != null ? expected : value;
    }

    // Getters
    public String getType() { return type; }
    public String getField() { return field; }
    public Object getValue() { return value; }
    public String getName() { return name; }
    public String getExpected() { return expected; }
    public List<Condition> getAnd() { return and; }
    public List<Condition> getOr() { return or; }

    // Setters
    public void setType(String type) { this.type = type; }
    public void setField(String field) { this.field = field; }
    public void setValue(Object value) { this.value = value; }
    public void setName(String name) { this.name = name; }
    public void setExpected(String expected) { this.expected = expected; }
    public void setAnd(List<Condition> and) { this.and = and; }
    public void setOr(List<Condition> or) { this.or = or; }

    // toString for debugging
    @Override
    public String toString() {
        return "Condition{" +
                "type='" + type + '\'' +
                ", field='" + field + '\'' +
                ", value=" + value +        
                ", name='" + name + '\'' +
                ", expected='" + expected + '\'' +
                ", and=" + and +
                ", or=" + or +
                '}';
    }
}