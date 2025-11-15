package com.example.demoproject.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.Map;

public class DataTransformer {

    public static Object applyTransform(Object value, Object transformSpec) {
        if (transformSpec == null) {
            return value;
        }

        if (transformSpec instanceof String) {
            return applyBuiltin((String) transformSpec, value, null);
        } else if (transformSpec instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tMap = (Map<String, Object>) transformSpec;
            String name = (String) tMap.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) tMap.getOrDefault("args", Map.of());
            return applyBuiltin(name, value, args);
        }

        return value;
    }

    private static Object applyBuiltin(String name, Object value, Map<String, Object> args) {
        if (value == null) return "";

        switch (name) {
            case "showPremium":
                return "showPremiumBadge";
            case "toUpperCase":
                return value.toString().toUpperCase();

            case "toLowerCase":
                return value.toString().toLowerCase();

            case "formatCurrency":
                return formatCurrency(value, args);

            case "formatDate":
                return formatDate(value, args);

            case "maskEmail":
                return maskEmail(value.toString());

            default:
                throw new IllegalArgumentException("Unknown transform: " + name);
        }
    }

    private static Object formatCurrency(Object value, Map<String, Object> args) {
        String symbol = (String) args.getOrDefault("currencySymbol", "$");
        int decimals = (int) args.getOrDefault("decimalPlaces", 2);

        try {
            double num = Double.parseDouble(value.toString());
            BigDecimal bd = BigDecimal.valueOf(num).setScale(decimals, RoundingMode.HALF_UP);
            return symbol + bd.toString();
        } catch (NumberFormatException e) {
            return "[Invalid Amount]";
        }
    }

    private static Object formatDate(Object value, Map<String, Object> args) {
        String pattern = (String) args.getOrDefault("pattern", "yyyy-MM-dd");
        try {
            Instant instant;
            if (value instanceof String) {
                instant = Instant.parse((String) value); // ISO format
            } else if (value instanceof Number) {
                long ts = ((Number) value).longValue();
                instant = Instant.ofEpochMilli(ts);
            } else {
                return value.toString();
            }
            return DateTimeFormatter.ofPattern(pattern)
                    .withZone(ZoneId.systemDefault())
                    .format(instant);
        } catch (Exception e) {
            return "[Invalid Date]";
        }
    }

    private static String maskEmail(String email) {
        if (!email.contains("@")) return email;
        String[] parts = email.split("@", 2);
        String user = parts[0];
        if (user.length() <= 2) return email;
        return user.charAt(0) + "***" + user.charAt(user.length() - 1) + "@" + parts[1];
    }
}