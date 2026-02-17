package com.example.demo.docgen.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for plan comparison matrix transformation.
 * Demonstrates the complete flow from nested plan data to Excel-ready 2D matrix.
 */
public class PlanComparisonTransformerTest {

    /**
     * Example 1: Basic transformation with 3 plans and 5 benefits
     * Benefits are in different order across plans (benefits are normalized for matching)
     */
    @Test
    public void testTransformPlansWithDifferentBenefitOrder() {
        // Input: Plans with benefits in different order
        List<Map<String, Object>> plans = Arrays.asList(
                createPlan("Basic", Arrays.asList(
                        createBenefit("Doctor Visits", "$20 copay"),
                        createBenefit("Prescriptions", "$10 copay"),
                        createBenefit("Hospital", "20% coinsurance"),
                        createBenefit("Dental", "50% coverage"),
                        createBenefit("Vision", "$25 copay")
                )),
                createPlan("Premium", Arrays.asList(
                        // Benefits in different order
                        createBenefit("Prescriptions", "$5 copay"),
                        createBenefit("Doctor Visits", "Covered 100%"),
                        createBenefit("Vision", "$10 copay"),
                        createBenefit("Hospital", "10% coinsurance"),
                        createBenefit("Dental", "80% coverage")
                )),
                createPlan("Elite", Arrays.asList(
                        // And another different order
                        createBenefit("Vision", "Covered 100%"),
                        createBenefit("Hospital", "0% coinsurance"),
                        createBenefit("Dental", "100% coverage"),
                        createBenefit("Doctor Visits", "Covered 100%"),
                        createBenefit("Prescriptions", "Free")
                ))
        );

        // Transform
        List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);

        // Verify structure
        assertNotNull(matrix);
        assertEquals(6, matrix.size()); // 1 header + 5 benefits

        // Verify header row: ["Benefit", "", "Basic", "", "Premium", "", "Elite"]
        List<Object> headerRow = matrix.get(0);
        assertEquals(7, headerRow.size()); // 1 benefit col + 3 plans + 2 spacers
        assertEquals("Benefit", headerRow.get(0));
        assertEquals("Basic", headerRow.get(2));
        assertEquals("Premium", headerRow.get(4));
        assertEquals("Elite", headerRow.get(6));

        // Verify benefit rows
        List<Object> doctorRow = matrix.get(1); // Doctor Visits (first benefit by first occurrence)
        assertEquals("Doctor Visits", doctorRow.get(0));
        assertEquals("$20 copay", doctorRow.get(2));  // Basic
        assertEquals("Covered 100%", doctorRow.get(4)); // Premium
        assertEquals("Covered 100%", doctorRow.get(6)); // Elite

        List<Object> prescriptionRow = matrix.get(2); // Prescriptions
        assertEquals("Prescriptions", prescriptionRow.get(0));
        assertEquals("$10 copay", prescriptionRow.get(2));  // Basic
        assertEquals("$5 copay", prescriptionRow.get(4)); // Premium
        assertEquals("Free", prescriptionRow.get(6)); // Elite

        System.out.println("✓ Matrix transformation successful:");
        printMatrix(matrix);
    }

    /**
     * Example 2: Plans with benefits that don't exist in all plans
     */
    @Test
    public void testTransformWithMissingBenefits() {
        List<Map<String, Object>> plans = Arrays.asList(
                createPlan("Plan A", Arrays.asList(
                        createBenefit("Benefit 1", "Value A1"),
                        createBenefit("Benefit 2", "Value A2")
                )),
                createPlan("Plan B", Arrays.asList(
                        createBenefit("Benefit 1", "Value B1"),
                        createBenefit("Benefit 2", "Value B2"),
                        createBenefit("Benefit 3", "Value B3")  // Extra benefit
                ))
        );

        List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);

        assertEquals(4, matrix.size()); // 1 header + 3 benefits

        // Benefit 3 row should have empty value for Plan A
        List<Object> benefit3Row = matrix.get(3);
        assertEquals("Benefit 3", benefit3Row.get(0));
        assertEquals("", benefit3Row.get(2));  // Plan A (missing)
        assertEquals("Value B3", benefit3Row.get(4)); // Plan B

        System.out.println("✓ Matrix with missing benefits:");
        printMatrix(matrix);
    }

    /**
     * Example 3: Data injection into template context
     */
    @Test
    public void testInjectComparisonMatrix() {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("company", "HealthCorp");
        templateData.put("effectiveDate", "2026-03-01");

        List<Map<String, Object>> plans = Arrays.asList(
                createPlan("Bronze", Arrays.asList(
                        createBenefit("Doctor Visit", "$40"),
                        createBenefit("Lab", "Covered")
                )),
                createPlan("Silver", Arrays.asList(
                        createBenefit("Doctor Visit", "$20"),
                        createBenefit("Lab", "Covered")
                ))
        );

        // Inject matrix into data
        Map<String, Object> result = PlanComparisonTransformer.injectComparisonMatrix(templateData, plans);

        assertTrue(result.containsKey("comparisonMatrix"));
        assertEquals("HealthCorp", result.get("company")); // Original data preserved
        assertEquals("2026-03-01", result.get("effectiveDate"));

        List<List<Object>> matrix = (List<List<Object>>) result.get("comparisonMatrix");
        assertEquals(3, matrix.size()); // 1 header + 2 benefits

        System.out.println("✓ Matrix injected into template data:");
        printMatrix(matrix);
    }

    /**
     * Example 4: Custom spacing width (2 columns instead of 1)
     */
    @Test
    public void testCustomSpacingWidth() {
        List<Map<String, Object>> plans = Arrays.asList(
                createPlan("Plan A", Arrays.asList(createBenefit("Benefit 1", "A1"))),
                createPlan("Plan B", Arrays.asList(createBenefit("Benefit 1", "B1")))
        );

        // 2-column spacing
        List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans, "name", "value", 2);

        // Header: ["Benefit", "", "", "Plan A", "", "", "Plan B"]
        List<Object> headerRow = matrix.get(0);
        assertEquals(7, headerRow.size()); // 1 benefit + (2 space + 1 plan) * 2
        assertEquals("Plan A", headerRow.get(3));
        assertEquals("Plan B", headerRow.get(6));

        System.out.println("✓ Matrix with 2-column spacing:");
        printMatrix(matrix);
    }

    /**
     * Real-world example: Insurance plan comparison
     */
    @Test
    public void testRealWorldInsuranceComparison() {
        List<Map<String, Object>> plans = Arrays.asList(
                createPlan("Standard", Arrays.asList(
                        createBenefit("Doctor Visit", "$50 copay"),
                        createBenefit("ER Visit", "$250 copay"),
                        createBenefit("Hospital Stay", "$1000 deductible"),
                        createBenefit("Prescription", "$15/$35 copay")
                )),
                createPlan("Preferred", Arrays.asList(
                        createBenefit("Doctor Visit", "$25 copay"),
                        createBenefit("Prescription", "$10/$25 copay"),
                        createBenefit("ER Visit", "$150 copay"),
                        createBenefit("Hospital Stay", "$500 deductible")
                )),
                createPlan("Premium Plus", Arrays.asList(
                        createBenefit("ER Visit", "Covered 100%"),
                        createBenefit("Doctor Visit", "$10 copay"),
                        createBenefit("Prescription", "$5/$15 copay"),
                        createBenefit("Hospital Stay", "$250 deductible")
                ))
        );

        Map<String, Object> fullData = new HashMap<>();
        fullData.put("comparisonYear", 2026);
        fullData.put("effectiveDate", "March 1, 2026");

        Map<String, Object> enrichedData = PlanComparisonTransformer.injectComparisonMatrix(fullData, plans);

        printMatrix((List<List<Object>>) enrichedData.get("comparisonMatrix"));
        System.out.println("\nFull data for template: " + enrichedData);
    }

    // ==================== Helper Methods ====================

    private static Map<String, Object> createPlan(String planName, List<Map<String, Object>> benefits) {
        Map<String, Object> plan = new HashMap<>();
        plan.put("planName", planName);
        plan.put("benefits", benefits);
        return plan;
    }

    private static Map<String, Object> createBenefit(String name, Object value) {
        Map<String, Object> benefit = new HashMap<>();
        benefit.put("name", name);
        benefit.put("value", value);
        return benefit;
    }

    private static void printMatrix(List<List<Object>> matrix) {
        System.out.println("\nComparison Matrix:");
        for (int i = 0; i < matrix.size(); i++) {
            List<Object> row = matrix.get(i);
            System.out.print("Row " + i + ": ");
            for (Object cell : row) {
                System.out.print("[" + (cell != null ? cell.toString() : "null") + "] ");
            }
            System.out.println();
        }
    }
}
