package com.example.demo.docgen.controller;

import com.example.demo.docgen.util.PlanComparisonTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for plan comparison Excel generation.
 * Demonstrates how to use the plan comparison feature in a real scenario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PlanComparisonExcelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Real-world scenario: Health insurance plan comparison
     * 
     * This test demonstrates:
     * 1. Creating request data with plans and benefits
     * 2. Transforming the data using PlanComparisonTransformer
     * 3. Sending to Excel generation endpoint
     * 4. Receiving XLSX file
     */
    @Test
    public void testGeneratePlanComparisonExcel() throws Exception {
        // Step 1: Create request data with nested plan structure
        // (This would typically come from your UI or API)
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("comparisonTitle", "2026 Health Insurance Plan Comparison");
        requestData.put("effectiveDate", "March 1, 2026");
        
        // Nested plans with benefits
        List<Map<String, Object>> plans = Arrays.asList(
                createPlan("Basic", Arrays.asList(
                        createBenefit("Doctor Visits", "$20 copay"),
                        createBenefit("ER Visit", "$250 copay"),
                        createBenefit("Hospital Stay", "$1,000 deductible"),
                        createBenefit("Prescription", "$10/$35 copay")
                )),
                createPlan("Premium", Arrays.asList(
                        createBenefit("Prescription", "$5 copay"),
                        createBenefit("Doctor Visits", "Covered 100%"),
                        createBenefit("ER Visit", "$150 copay"),
                        createBenefit("Hospital Stay", "$500 deductible")
                )),
                createPlan("Elite", Arrays.asList(
                        createBenefit("ER Visit", "Covered 100%"),
                        createBenefit("Hospital Stay", "$250 deductible"),
                        createBenefit("Doctor Visits", "Covered 100%"),
                        createBenefit("Prescription", "Free")
                ))
        );

        // Step 2: Transform nested plan data into comparison matrix
        Map<String, Object> templateData = PlanComparisonTransformer.injectComparisonMatrix(
                requestData,
                plans,
                "name",      // benefit name field
                "value",     // benefit value field
                1            // 1-column spacing between plans
        );

        // Step 3: Send to Excel generation endpoint
        String requestBody = objectMapper.writeValueAsString(new DocumentGenerationRequest(
                "common-templates",      // namespace
                "plan-comparison",       // templateId (the YAML template we created)
                templateData             // data with injected comparisonMatrix
        ));

        mockMvc.perform(post("/api/documents/generate/excel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());

        System.out.println("✓ Successfully generated plan comparison Excel");
        System.out.println("\nRequest Data Structure:");
        System.out.println("  - Original: comparisonTitle, effectiveDate");
        System.out.println("  - Injected: comparisonMatrix (2D array)");
        System.out.println("\nComparison Matrix Structure:");
        List<List<Object>> matrix = (List<List<Object>>) templateData.get("comparisonMatrix");
        System.out.println("  - Rows: " + matrix.size() + " (1 header + " + (matrix.size() - 1) + " benefits)");
        System.out.println("  - Columns: " + matrix.get(0).size() + " (benefit + plans with spacing)");
        System.out.println("\nMatrix preview:");
        for (int i = 0; i < Math.min(3, matrix.size()); i++) {
            System.out.println("  Row " + i + ": " + matrix.get(i));
        }
    }

    /**
     * Alternative scenario: Using custom field names and spacing
     */
    @Test
    public void testPlanComparisonWithCustomFields() throws Exception {
        List<Map<String, Object>> plans = Arrays.asList(
                createPlanCustom("Plan A", "planCode", "A", Arrays.asList(
                        createBenefitCustom("Feature 1", "featureDescription", "Available"),
                        createBenefitCustom("Feature 2", "featureDescription", "Premium Only")
                ))
        );

        // Using custom field mapping
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> result = PlanComparisonTransformer.injectComparisonMatrix(
                data,
                plans,
                "name",                // benefit name field
                "value",               // benefit value field
                2                      // 2-column spacing
        );

        assertTrue(result.containsKey("comparisonMatrix"));
        System.out.println("✓ Custom field transformation successful");
    }

    // ==================== Helper Classes ====================

    /**
     * Request DTO matching controller expectations
     */
    public static class DocumentGenerationRequest {
        public String namespace;
        public String templateId;
        public Map<String, Object> data;

        public DocumentGenerationRequest(String namespace, String templateId, Map<String, Object> data) {
            this.namespace = namespace;
            this.templateId = templateId;
            this.data = data;
        }

        public String getNamespace() { return namespace; }
        public String getTemplateId() { return templateId; }
        public Map<String, Object> getData() { return data; }
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

    private static Map<String, Object> createPlanCustom(String planName, String codeField, String code, List<Map<String, Object>> benefits) {
        Map<String, Object> plan = new HashMap<>();
        plan.put("planName", planName);
        plan.put(codeField, code);
        plan.put("benefits", benefits);
        return plan;
    }

    private static Map<String, Object> createBenefitCustom(String name, String descField, String value) {
        Map<String, Object> benefit = new HashMap<>();
        benefit.put("name", name);
        benefit.put(descField, value);
        benefit.put("included", value);
        return benefit;
    }

    private void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Assertion failed");
        }
    }
}
