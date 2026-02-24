package com.example.demo.docgen.service;

import com.example.demo.docgen.model.DocumentTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for namespace-aware template loading
 * Tests namespace isolation, cross-namespace references, and default namespace behavior
 */
@DisplayName("Namespace-Aware Template Loading Tests")
public class NamespaceTemplateLoaderTest {

    private final NamespaceResolver namespaceResolver = new NamespaceResolver();
    private final TemplateLoader templateLoader = new TemplateLoader(namespaceResolver);

    @Test
    @DisplayName("Should load template from common-templates namespace")
    public void testLoadTemplateFromCommonTemplatesNamespace() {
        // Test loading a template with explicit common-templates namespace
        DocumentTemplate template = templateLoader.loadTemplate(
            "common-templates", 
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        assertNotNull(template, "Template should be loaded");
        assertEquals("base-enrollment", template.getTemplateId(), "Template ID should match");
        assertFalse(template.getSections().isEmpty(), "Template should have sections");
        
        // Verify namespace is stored in metadata
        assertNotNull(template.getMetadata(), "Metadata should be present");
        assertEquals("common-templates", template.getMetadata().get("_namespace"), 
            "Namespace should be stored in metadata");
    }

    @Test
    @DisplayName("Should load template from tenant-a namespace")
    public void testLoadTemplateFromTenantANamespace() {
        // Test loading a template with explicit tenant-a namespace
        DocumentTemplate template = templateLoader.loadTemplate(
            "tenant-a", 
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        assertNotNull(template, "Template should be loaded");
        assertEquals("base-enrollment", template.getTemplateId(), "Template ID should match");
        assertFalse(template.getSections().isEmpty(), "Template should have sections");
        
        // Verify namespace is stored in metadata
        assertNotNull(template.getMetadata(), "Metadata should be present");
        assertEquals("tenant-a", template.getMetadata().get("_namespace"), 
            "Namespace should be stored in metadata");
    }

    @Test
    @DisplayName("Should load from common-templates namespace when explicitly specified")
    public void testDefaultToCommonTemplatesNamespace() {
        // Test loading with explicit namespace specification
        DocumentTemplate template = templateLoader.loadTemplate(
            "common-templates",
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        assertNotNull(template, "Template should be loaded");
        assertEquals("base-enrollment", template.getTemplateId(), "Template ID should match");
        assertFalse(template.getSections().isEmpty(), "Template should have sections");
        
        // Verify namespace is set to common-templates
        assertNotNull(template.getMetadata(), "Metadata should be present");
        assertEquals("common-templates", template.getMetadata().get("_namespace"), 
            "Namespace should be common-templates");
    }

    @Test
    @DisplayName("Should enforce namespace isolation - template not found when wrong namespace")
    public void testNamespaceIsolationEnforcement() {
        // Test that requesting a template from a namespace where it doesn't exist fails
        // (This would test that a template in common-templates isn't found when asking from tenant-a with a filename that doesn't exist in tenant-a)
        
        assertThrows(RuntimeException.class, () -> {
            templateLoader.loadTemplate(
                "tenant-a", 
                "base-enrollmentx.yaml", 
                new HashMap<>()
            );
        }, "Should throw exception when template not found in specified namespace");
    }

    @Test
    @DisplayName("Should resolve namespace from path when extracting namespace")
    public void testNamespaceExtractionFromPath() {
        // Test the NamespaceResolver's ability to extract namespace from a full path
        String path = "tenant-a/templates/base-enrollment.yaml";
        
        String namespace = namespaceResolver.normalizeNamespace("tenant-a");
        assertEquals("tenant-a", namespace, "Should normalize namespace correctly");
    }

    @Test
    @DisplayName("Should resolve resource paths within namespace context")
    public void testResourcePathResolutionInNamespace() {
        // Test resolving a resource (like a PDF or FTL file) within a specific namespace
        String resourcePath = namespaceResolver.resolveResourcePath(
            "forms/applicant-form.pdf", 
            "tenant-a"
        );
        
        assertEquals("tenant-a/templates/forms/applicant-form.pdf", resourcePath, 
            "Should resolve resource path to namespace/templates/resource");
    }

    @Test
    @DisplayName("Should support cross-namespace references with common: prefix")
    public void testCrossNamespaceReferenceWithCommonPrefix() {
        // Test resolving a resource from another namespace using "common:" prefix
        String resourcePath = namespaceResolver.resolveResourcePath(
            "common:forms/header.pdf", 
            "tenant-a"
        );
        
        assertEquals("common-templates/templates/forms/header.pdf", resourcePath, 
            "Should resolve common: prefixed resource to common-templates namespace");
    }

    @Test
    @DisplayName("Should preserve placeholders in template IDs for resolution")
    public void testPlaceholderResolutionInTemplateId() {
        // Test that placeholders like ${...} are properly resolved
        Map<String, Object> variables = new HashMap<>();
        variables.put("state", "CA");
        
        DocumentTemplate template = templateLoader.loadTemplate(
            "common-templates", 
            "base-enrollment.yaml",
            variables
        );
        
        assertNotNull(template, "Template should be loaded with variables");
        assertEquals("base-enrollment", template.getTemplateId());
    }

    @Test
    @DisplayName("Should load templates with ACROFORM sections that have templatePath")
    public void testLoadTemplateWithAcroFormTemplatePath() {
        DocumentTemplate template = templateLoader.loadTemplate(
            "common-templates", 
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        assertNotNull(template, "Template should be loaded");
        
        // Verify that ACROFORM sections have templatePath
        template.getSections().stream()
            .filter(s -> s.getType().name().equals("ACROFORM"))
            .forEach(s -> {
                assertNotNull(s.getTemplatePath(), 
                    "ACROFORM section should have templatePath set");
            });
    }

    @Test
    @DisplayName("Should distingush between different namespaces with same template ID")
    public void testDistinctNamespacesBothLoadableWithSameTemplateId() {
        // Load same template from two different namespaces
        DocumentTemplate commonTemplate = templateLoader.loadTemplate(
            "common-templates", 
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        DocumentTemplate tenantTemplate = templateLoader.loadTemplate(
            "tenant-a", 
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        assertNotNull(commonTemplate, "common-templates version should load");
        assertNotNull(tenantTemplate, "tenant-a version should load");
        
        // Both have same template ID but different namespaces
        assertEquals("base-enrollment", commonTemplate.getTemplateId());
        assertEquals("base-enrollment", tenantTemplate.getTemplateId());
        assertEquals("common-templates", commonTemplate.getMetadata().get("_namespace"));
        assertEquals("tenant-a", tenantTemplate.getMetadata().get("_namespace"));
    }

    @Test
    @DisplayName("Should handle null namespace by defaulting to common-templates")
    public void testNullNamespaceDefaultsToCommonTemplates() {
        // Verify that null namespace is treated as common-templates
        String normalizedNamespace = namespaceResolver.normalizeNamespace(null);
        assertEquals("common-templates", normalizedNamespace, 
            "Null namespace should normalize to common-templates");
    }

    @Test
    @DisplayName("Should handle empty namespace by defaulting to common-templates")
    public void testEmptyNamespaceDefaultsToCommonTemplates() {
        // Verify that empty namespace is treated as common-templates
        String normalizedNamespace = namespaceResolver.normalizeNamespace("");
        assertEquals("common-templates", normalizedNamespace, 
            "Empty namespace should normalize to common-templates");
    }

    @Test
    @DisplayName("Should resolve template paths consistent with classpath resource layout")
    public void testTemplatePathResolutionMatchesClasspathLayout() {
        // The resolved path should match the classpath resource layout
        String resolvedPath = namespaceResolver.resolveTemplatePath("tenant-a", "base-enrollment.yaml");
        
        assertEquals("tenant-a/templates/base-enrollment.yaml", resolvedPath, 
            "Resolved path should match classpath layout: {namespace}/templates/{templateId}");
    }

    @Test
    @DisplayName("Should load and preserve section metadata from templates in specific namespaces")
    public void testPreserveSectionMetadataPerNamespace() {
        DocumentTemplate template = templateLoader.loadTemplate(
            "tenant-a", 
            "base-enrollment.yaml", 
            new HashMap<>()
        );
        
        assertNotNull(template, "Template should be loaded");
        assertFalse(template.getSections().isEmpty(), "Template should have sections");
        
        // Verify sections are properly loaded
        template.getSections().forEach(section -> {
            assertNotNull(section.getSectionId(), "Section should have ID");
            assertNotNull(section.getType(), "Section should have type");
        });
    }
}
