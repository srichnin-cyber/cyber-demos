package com.example.demo.docgen.service;

import com.example.demo.docgen.model.DocumentTemplate;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates prewarming templates with placeholder variables.
 * 
 * Use case: Load templates at startup, resolve placeholders with known variable scenarios
 * (e.g., tenant defaults, environment variants), and cache the resolved versions.
 */
@DisplayName("Cache Warming with Placeholder Variables")
public class TemplateCacheWarmerWithVariablesTest {

    private TemplateLoader templateLoader;
    private NamespaceResolver namespaceResolver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        namespaceResolver = new NamespaceResolver();
        templateLoader = new TemplateLoader(namespaceResolver);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should load template with variables and resolve placeholders in templatePath")
    void testLoadTemplateWithVariablesResolvesPlaceholders() {
        // Given: variables map with known values
        Map<String, Object> variables = new HashMap<>();
        variables.put("formType", "enrollment");
        variables.put("version", "v1");
        variables.put("region", "CA");
        variables.put("environment", "production");
        variables.put("isFormRequired", true);

        // When: Load template without variables first (as would happen at startup)
        DocumentTemplate baseTemplate = templateLoader.loadTemplate("test-placeholder-template.yaml");
        assertNotNull(baseTemplate, "Template with placeholders should load");

        // Create a copy for interpolation (to avoid mutating cached original)
        DocumentTemplate resolvedTemplate = deepCopy(baseTemplate);

        // Before interpolation: has placeholders
        boolean hasPlaceholders = resolvedTemplate.getSections().stream()
            .anyMatch(s -> s.getTemplatePath() != null && s.getTemplatePath().contains("${"));
        assertTrue(hasPlaceholders, "Template should have placeholders before interpolation");

        // Interpolate with known variables
        templateLoader.interpolateTemplateFields(resolvedTemplate, variables);

        // After interpolation: placeholders resolved
        boolean stillHasPlaceholders = resolvedTemplate.getSections().stream()
            .anyMatch(s -> s.getTemplatePath() != null && s.getTemplatePath().contains("${"));
        assertFalse(stillHasPlaceholders, "All placeholders should be resolved");

        // Verify the template structure is intact
        assertNotNull(resolvedTemplate.getSections(), "Template should have sections");
        assertFalse(resolvedTemplate.getSections().isEmpty(), "Sections should not be empty");
    }

    @Test
    @DisplayName("Should preserve placeholder syntax and resolve during interpolation")
    void testPlaceholderInterpolationWithVariables() {
        // Create a template section with placeholder in templatePath
        PageSection section = PageSection.builder()
                .sectionId("dynamic-form")
                .type(SectionType.ACROFORM)
                .templatePath("forms/${formType}-form-${version}.pdf")
                .order(1)
                .build();

        DocumentTemplate template = new DocumentTemplate();
        template.setTemplateId("test-dynamic");
        template.setSections(List.of(section));

        // Before interpolation: placeholder is present
        assertEquals("forms/${formType}-form-${version}.pdf", 
            template.getSections().get(0).getTemplatePath(),
            "Before interpolation, templatePath should contain placeholders");

        // Create variables map
        Map<String, Object> variables = new HashMap<>();
        variables.put("formType", "enrollment");
        variables.put("version", "v1");

        // Interpolate
        templateLoader.interpolateTemplateFields(template, variables);

        // After interpolation: placeholders replaced
        assertEquals("forms/enrollment-form-v1.pdf", 
            template.getSections().get(0).getTemplatePath(),
            "After interpolation, templatePath should have values substituted");
    }

    @Test
    @DisplayName("Should support multiple variable scenarios for prewarming")
    void testPrewarmMultipleVariantScenarios() {
        // Scenario 1: Production defaults
        Map<String, Object> prodVars = new HashMap<>();
        prodVars.put("formType", "enrollment");
        prodVars.put("version", "2024-prod");
        prodVars.put("region", "us-east-1");
        prodVars.put("environment", "production");
        prodVars.put("isFormRequired", true);

        // Scenario 2: Development defaults
        Map<String, Object> devVars = new HashMap<>();
        devVars.put("formType", "enrollment");
        devVars.put("version", "2024-dev");
        devVars.put("region", "local");
        devVars.put("environment", "development");
        devVars.put("isFormRequired", true);

        // Scenario 3: Staging defaults
        Map<String, Object> stagingVars = new HashMap<>();
        stagingVars.put("formType", "enrollment");
        stagingVars.put("version", "2024-staging");
        stagingVars.put("region", "us-west-2");
        stagingVars.put("environment", "staging");
        stagingVars.put("isFormRequired", true);

        // Simulate warming cache with each scenario
        List<Map<String, Object>> scenarios = List.of(prodVars, devVars, stagingVars);
        Map<String, DocumentTemplate> resolvedCache = new HashMap<>();

        for (Map<String, Object> vars : scenarios) {
            String environment = (String) vars.get("environment");
            
            // Load template with real placeholders from classpath
            DocumentTemplate base = templateLoader.loadTemplate("test-placeholder-template.yaml");
            
            // Create copy and interpolate for this scenario
            DocumentTemplate resolved = deepCopy(base);
            templateLoader.interpolateTemplateFields(resolved, vars);
            
            // Store in scenario-specific cache
            String cacheKey = "enrollment:" + environment;
            resolvedCache.put(cacheKey, resolved);
        }

        // Verify all scenarios cached
        assertEquals(3, resolvedCache.size(), "Should cache 3 scenario variants");
        assertTrue(resolvedCache.containsKey("enrollment:production"), 
            "Should have production variant");
        assertTrue(resolvedCache.containsKey("enrollment:development"), 
            "Should have development variant");
        assertTrue(resolvedCache.containsKey("enrollment:staging"), 
            "Should have staging variant");
        
        // Verify each cached template has resolved placeholders
        resolvedCache.values().forEach(template -> {
            boolean hasPlaceholders = template.getSections().stream()
                .anyMatch(s -> s.getTemplatePath() != null && s.getTemplatePath().contains("${"));
            assertFalse(hasPlaceholders, "Cached resolved template should have no placeholders");
        });
    }

    @Test
    @DisplayName("Should handle nested variable resolution in templates")
    void testNestedVariableResolution() {
        // Map with nested structure
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put("formVersion", "2.0");
        config.put("region", "CA");
        variables.put("config", config);
        variables.put("appName", "enrollment-system");

        // Create section with nested placeholder reference
        PageSection section = PageSection.builder()
                .sectionId("config-aware")
                .type(SectionType.ACROFORM)
                .templatePath("forms/${appName}-${config.formVersion}-${config.region}.pdf")
                .condition("$.config.region == 'CA'")
                .order(1)
                .build();

        DocumentTemplate template = new DocumentTemplate();
        template.setTemplateId("nested-test");
        template.setSections(List.of(section));

        // Before interpolation
        assertTrue(template.getSections().get(0).getTemplatePath().contains("${"),
            "Should have placeholders before interpolation");

        // Interpolate
        templateLoader.interpolateTemplateFields(template, variables);

        // After interpolation - nested paths resolved
        String resolvedPath = template.getSections().get(0).getTemplatePath();
        assertTrue(resolvedPath.contains("enrollment-system"), 
            "Should resolve appName");
        assertTrue(resolvedPath.contains("2.0"), 
            "Should resolve nested config.formVersion");
        assertTrue(resolvedPath.contains("CA"), 
            "Should resolve nested config.region");
        assertFalse(resolvedPath.contains("${"), 
            "Should have no remaining placeholders");
    }

    @Test
    @DisplayName("Should demonstrate safe cached template copying before interpolation")
    void testSafeCachedTemplateCopyBeforeInterpolation() {
        // 1. Load and cache template (normally done at startup)
        DocumentTemplate cachedTemplate = templateLoader.loadTemplate("test-placeholder-template.yaml");
        String cachedTemplatePath = cachedTemplate.getSections().stream()
            .filter(s -> s.getSectionId().equals("form-section"))
            .map(PageSection::getTemplatePath)
            .findFirst()
            .orElse("");
        
        assertTrue(cachedTemplatePath.contains("${"), "Cached should have placeholders initially");

        // 2. Create two independent requests with different variables
        Map<String, Object> request1Vars = new HashMap<>();
        request1Vars.put("formType", "type-a");
        request1Vars.put("version", "v1");
        request1Vars.put("region", "CA");
        request1Vars.put("environment", "prod");
        request1Vars.put("isFormRequired", true);

        Map<String, Object> request2Vars = new HashMap<>();
        request2Vars.put("formType", "type-b");
        request2Vars.put("version", "v2");
        request2Vars.put("region", "NY");
        request2Vars.put("environment", "staging");
        request2Vars.put("isFormRequired", true);

        // 3. For REQUEST 1: Copy cached template, interpolate
        DocumentTemplate req1Template = deepCopy(cachedTemplate);
        templateLoader.interpolateTemplateFields(req1Template, request1Vars);

        // 4. For REQUEST 2: Copy cached template again, interpolate with different values
        DocumentTemplate req2Template = deepCopy(cachedTemplate);
        templateLoader.interpolateTemplateFields(req2Template, request2Vars);

        // 5. Verify cached template was NOT mutated
        String currentCachedPath = cachedTemplate.getSections().stream()
            .filter(s -> s.getSectionId().equals("form-section"))
            .map(PageSection::getTemplatePath)
            .findFirst()
            .orElse("");
        
        assertEquals(cachedTemplatePath, currentCachedPath,
            "Cached template should not be mutated by per-request interpolation");
        assertTrue(currentCachedPath.contains("${"), 
            "Cached template should still have placeholders");

        // 6. Verify both requests received independent copies with their own values
        String req1Path = req1Template.getSections().stream()
            .filter(s -> s.getSectionId().equals("form-section"))
            .map(PageSection::getTemplatePath)
            .findFirst()
            .orElse("");
        
        String req2Path = req2Template.getSections().stream()
            .filter(s -> s.getSectionId().equals("form-section"))
            .map(PageSection::getTemplatePath)
            .findFirst()
            .orElse("");
        
        assertTrue(req1Path.contains("type-a"), "Request 1 should have type-a");
        assertTrue(req1Path.contains("v1"), "Request 1 should have v1");
        assertTrue(req1Path.contains("CA"), "Request 1 should have CA");
        assertFalse(req1Path.contains("${"), "Request 1 should have no placeholders");
        
        assertTrue(req2Path.contains("type-b"), "Request 2 should have type-b");
        assertTrue(req2Path.contains("v2"), "Request 2 should have v2");
        assertTrue(req2Path.contains("NY"), "Request 2 should have NY");
        assertFalse(req2Path.contains("${"), "Request 2 should have no placeholders");
        
        assertNotEquals(req1Path, req2Path, 
            "Different requests should have different variable resolutions");
    }

    @Test
    @DisplayName("Should support cache key generation from variables for resolved-template cache")
    void testCacheKeyGenerationFromVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("state", "CA");
        vars.put("formType", "enrollment");
        vars.put("version", "2024-q1");

        // Generate deterministic cache key from variables
        String cacheKey = generateCacheKeyFromVariables("tenant-a", "enrollment-form", vars);

        // Verify key is stable across calls with same values
        String cacheKey2 = generateCacheKeyFromVariables("tenant-a", "enrollment-form", vars);
        assertEquals(cacheKey, cacheKey2, 
            "Cache key should be deterministic for same variables");

        // Different variables produce different keys
        Map<String, Object> otherVars = new HashMap<>();
        otherVars.put("state", "NY");
        otherVars.put("formType", "enrollment");
        otherVars.put("version", "2024-q1");
        String otherKey = generateCacheKeyFromVariables("tenant-a", "enrollment-form", otherVars);
        
        assertNotEquals(cacheKey, otherKey, 
            "Different variables should produce different cache keys");
    }

    /**
     * Helper method to generate deterministic cache key from template identity and variables.
     * Uses SHA-256 hash of JSON-serialized variables for stability.
     */
    private String generateCacheKeyFromVariables(String namespace, String templateId, Map<String, Object> variables) {
        try {
            String varsJson = objectMapper.writeValueAsString(variables);
            String sha256Hash = sha256(varsJson);
            return namespace + ":" + templateId + ":" + sha256Hash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate cache key", e);
        }
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Deep copy a DocumentTemplate to avoid mutating cached instances.
     */
    private DocumentTemplate deepCopy(DocumentTemplate original) {
        try {
            String json = objectMapper.writeValueAsString(original);
            return objectMapper.readValue(json, DocumentTemplate.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deep copy template", e);
        }
    }

    /**
     * Helper assertion that templates differ in a specific way (for demo purposes).
     */
    private void documentAssertEquals(DocumentTemplate t1, DocumentTemplate t2, 
            String description, String fieldName, String expected1, String expected2, String message) {
        // This is a simplified assertion for demonstration
        assertNotNull(t1, "Template 1 should exist");
        assertNotNull(t2, "Template 2 should exist");
    }
}
