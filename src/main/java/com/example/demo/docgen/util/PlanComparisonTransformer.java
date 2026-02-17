package com.example.demo.docgen.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility to transform nested plan/benefits data into a 2D matrix for Excel comparison tables.
 * 
 * Input: plans with benefits in different orders
 * Output: 2D matrix with benefits in rows, plans in columns (with 1-column spacing)
 */
public class PlanComparisonTransformer {

    /**
     * Transform plans data with benefits into a 2D comparison matrix.
     * 
     * Example input:
     * {
     *   "plans": [
     *     { "planName": "Basic", "benefits": [ { "name": "Doctor Visits", "value": "$20 copay" }, ... ] },
     *     { "planName": "Premium", "benefits": [ { "name": "Doctor Visits", "value": "Covered 100%" }, ... ] }
     *   ]
     * }
     * 
     * Example output matrix:
     * [
     *   ["Benefit", "", "Basic", "", "Premium"],
     *   ["Doctor Visits", "", "$20 copay", "", "Covered 100%"],
     *   ["Prescriptions", "", "$10 copay", "", "$5 copay"]
     * ]
     * 
     * @param plans List of plan objects with planName and benefits array
     * @param benefitNameField Field name for benefit name (default: "name")
     * @param benefitValueField Field name for benefit value (default: "value")
     * @param spacingWidth Number of empty columns between plan columns (default: 1)
     * @return 2D array (List<List<String>>) suitable for 2D array Excel mapping
     */
    public static List<List<Object>> transformPlansToMatrix(
            List<Map<String, Object>> plans,
            String benefitNameField,
            String benefitValueField,
            int spacingWidth) {

        if (plans == null || plans.isEmpty()) {
            return new ArrayList<>();
        }

        // Step 1: Collect all unique benefit names (normalized) and their order
        Map<String, String> benefitNameMap = new LinkedHashMap<>(); // normalized -> display name
        for (Map<String, Object> plan : plans) {
            List<Map<String, Object>> benefits = (List<Map<String, Object>>) plan.get("benefits");
            if (benefits != null) {
                for (Map<String, Object> benefit : benefits) {
                    String name = (String) benefit.get(benefitNameField);
                    if (name != null) {
                        String normalized = name.toLowerCase().trim();
                        benefitNameMap.putIfAbsent(normalized, name); // Use first occurrence
                    }
                }
            }
        }

        List<String> benefitNames = new ArrayList<>(benefitNameMap.values());

        // Step 2: Create plan name list
        List<String> planNames = plans.stream()
                .map(p -> (String) p.get("planName"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Step 3: Build the benefit-to-value map for each plan
        Map<String, Map<String, Object>> planBenefitMap = new HashMap<>();
        for (Map<String, Object> plan : plans) {
            String planName = (String) plan.get("planName");
            Map<String, Object> benefitMap = new HashMap<>();

            List<Map<String, Object>> benefits = (List<Map<String, Object>>) plan.get("benefits");
            if (benefits != null) {
                for (Map<String, Object> benefit : benefits) {
                    String name = (String) benefit.get(benefitNameField);
                    Object value = benefit.get(benefitValueField);
                    if (name != null) {
                        String normalized = name.toLowerCase().trim();
                        benefitMap.put(normalized, value);
                    }
                }
            }

            planBenefitMap.put(planName, benefitMap);
        }

        // Step 4: Build the 2D matrix
        List<List<Object>> matrix = new ArrayList<>();

        // Header row: ["Benefit", "", "Plan1", "", "Plan2", "", ...]
        List<Object> headerRow = new ArrayList<>();
        headerRow.add("Benefit");
        for (int i = 0; i < planNames.size(); i++) {
            // Add spacing column(s)
            for (int s = 0; s < spacingWidth; s++) {
                headerRow.add("");
            }
            // Add plan name
            headerRow.add(planNames.get(i));
        }
        matrix.add(headerRow);

        // Data rows
        for (String displayBenefitName : benefitNames) {
            String normalizedBenefitName = displayBenefitName.toLowerCase().trim();
            List<Object> row = new ArrayList<>();
            row.add(displayBenefitName);

            for (String planName : planNames) {
                // Add spacing column(s)
                for (int s = 0; s < spacingWidth; s++) {
                    row.add("");
                }
                // Add benefit value for this plan
                Map<String, Object> benefitMap = planBenefitMap.get(planName);
                Object value = benefitMap != null ? benefitMap.get(normalizedBenefitName) : "";
                row.add(value != null ? value : "");
            }

            matrix.add(row);
        }

        return matrix;
    }

    /**
     * Convenience method with defaults: benefitNameField="name", benefitValueField="value", spacingWidth=1
     */
    public static List<List<Object>> transformPlansToMatrix(List<Map<String, Object>> plans) {
        return transformPlansToMatrix(plans, "name", "value", 1);
    }

    /**
     * Transform plans data and inject into a JSON object for template rendering.
     * 
     * @param data Original data object (Map)
     * @param plans List of plan objects
     * @return Updated data with "comparisonMatrix" field added
     */
    public static Map<String, Object> injectComparisonMatrix(
            Map<String, Object> data,
            List<Map<String, Object>> plans) {
        
        Map<String, Object> result = new HashMap<>(data);
        List<List<Object>> matrix = transformPlansToMatrix(plans);
        result.put("comparisonMatrix", matrix);
        return result;
    }

    /**
     * Convenience method to inject with custom field names.
     */
    public static Map<String, Object> injectComparisonMatrix(
            Map<String, Object> data,
            List<Map<String, Object>> plans,
            String benefitNameField,
            String benefitValueField,
            int spacingWidth) {
        
        Map<String, Object> result = new HashMap<>(data);
        List<List<Object>> matrix = transformPlansToMatrix(plans, benefitNameField, benefitValueField, spacingWidth);
        result.put("comparisonMatrix", matrix);
        return result;
    }
}
