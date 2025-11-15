package com.example.demoproject.service;

import com.jayway.jsonpath.DocumentContext;
import java.util.*;
import com.example.demoproject.model.Condition;
public class ConditionEvaluator {

    public static boolean evaluate(Condition cond, DocumentContext json, Object sourceValue) {
        if (cond == null) return true;

        // Handle compound conditions first
        if (cond.getAnd() != null && !cond.getAnd().isEmpty()) {
            return cond.getAnd().stream()
                .allMatch(c -> evaluate(c, json, resolveValueForCondition(c, json, sourceValue)));
        }
        if (cond.getOr() != null && !cond.getOr().isEmpty()) {
            return cond.getOr().stream()
                .anyMatch(c -> evaluate(c, json, resolveValueForCondition(c, json, sourceValue)));
        }

        // Simple conditions
        switch (cond.getType()) {
            case "notNull":
                return sourceValue != null && !"".equals(sourceValue.toString().trim());

            case "equals":
                Object compareValue = cond.getField() != null 
                    ? json.read("$." + cond.getField()) 
                    : sourceValue;
                return Objects.equals(compareValue, cond.getEffectiveValue());

            case "notEquals":
                Object neValue = cond.getField() != null 
                    ? json.read("$." + cond.getField()) 
                    : sourceValue;
                return !Objects.equals(neValue, cond.getEffectiveValue());

            case "greaterThan":
                return compareNumeric(sourceValue, cond.getEffectiveValue(), (a, b) -> a > b);

            case "lessThan":
                return compareNumeric(sourceValue, cond.getEffectiveValue(), (a, b) -> a < b);

            case "envVar":
                String actual = System.getenv(cond.getName());
                return Objects.equals(actual, cond.getEffectiveValue());

            case "property": // system property
                String prop = System.getProperty(cond.getName());
                return Objects.equals(prop, cond.getEffectiveValue());

            default:
                throw new IllegalArgumentException("Unsupported condition type: " + cond.getType());
        }
    }

    private static Object resolveValueForCondition(Condition c, DocumentContext json, Object fallback) {
        if (c.getField() != null) {
            return json.read("$." + c.getField());
        }
        return fallback;
    }

    private static boolean compareNumeric(Object actual, Object expected, java.util.function.BiFunction<Double, Double, Boolean> op) {
        try {
            double a = toDouble(actual);
            double b = toDouble(expected);
            return op.apply(a, b);
        } catch (Exception e) {
            return false;
        }
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        if (obj instanceof String) return Double.parseDouble((String) obj);
        throw new NumberFormatException("Cannot convert " + obj + " to number");
    }
}