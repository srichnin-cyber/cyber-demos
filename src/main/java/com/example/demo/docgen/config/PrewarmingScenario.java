package com.example.demo.docgen.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single prewarming scenario with template identity and variables.
 * 
 * Used to prewarm template cache with specific variable values at startup,
 * enabling resolved-template caching for known scenarios.
 * 
 * Example YAML:
 * scenarios:
 *   - name: "Production Enrollment"
 *     templateId: "enrollment-form"
 *     namespace: "tenant-a"
 *     description: "Prod defaults for enrollment form"
 *     variables:
 *       environment: production
 *       formType: enrollment
 *       version: 2024-prod
 *       region: us-east-1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrewarmingScenario {

    /**
     * Unique name for this scenario (e.g., "Production Enrollment", "Dev Staging")
     */
    private String name;

    /**
     * Template ID to load
     */
    private String templateId;

    /**
     * Namespace/tenant for the template (optional, defaults to common-templates)
     */
    private String namespace;

    /**
     * Optional description of this scenario
     */
    private String description;

    /**
     * Variables to interpolate into template placeholders.
     * Examples: environment, region, formType, version, etc.
     */
    private Map<String, Object> variables;

    /**
     * Whether to resolve header/footer/condition fields with these variables
     * (default: true)
     */
    @Builder.Default
    private boolean interpolateFields = true;

    public Map<String, Object> getVariables() {
        if (variables == null) {
            variables = new HashMap<>();
        }
        return variables;
    }
}
