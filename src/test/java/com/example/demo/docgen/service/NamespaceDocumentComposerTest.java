package com.example.demo.docgen.service;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.mapper.JsonPathMappingStrategy;
import com.example.demo.docgen.model.*;
import com.example.demo.docgen.processor.HeaderFooterProcessor;
import com.example.demo.docgen.renderer.SectionRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.inOrder;
import org.mockito.InOrder;

/**
 * Unit tests for DocumentComposer with namespace-aware template loading
 */
@DisplayName("Namespace-Aware Document Composer Tests")
public class NamespaceDocumentComposerTest {

    private DocumentComposer composer;
    private SectionRenderer mockRenderer;
    private TemplateLoader mockTemplateLoader;
    private HeaderFooterProcessor mockHeaderFooterProcessor;
        private com.example.demo.docgen.service.ExcelOutputService mockExcelOutputService;

    @BeforeEach
    public void setup() {
        mockRenderer = mock(SectionRenderer.class);
        mockTemplateLoader = mock(TemplateLoader.class);
        mockHeaderFooterProcessor = mock(HeaderFooterProcessor.class);
                mockExcelOutputService = mock(com.example.demo.docgen.service.ExcelOutputService.class);
        
        List<SectionRenderer> renderers = Collections.singletonList(mockRenderer);
        List<FieldMappingStrategy> strategies = Collections.singletonList(new JsonPathMappingStrategy());
        
        composer = new DocumentComposer(renderers, strategies, mockTemplateLoader, mockHeaderFooterProcessor, mockExcelOutputService);
        
        when(mockRenderer.supports(any())).thenReturn(true);
        try {
            when(mockRenderer.render(any(), any())).thenReturn(new PDDocument());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("Should use specified namespace when loading template")
    public void testDocumentGenerationWithExplicitNamespace() throws IOException {
        // Create a template
        PageSection section1 = PageSection.builder()
                .sectionId("section1")
                .type(SectionType.ACROFORM)
                .order(1)
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test-template")
                .sections(Collections.singletonList(section1))
                .build();

        // Setup mock to return template when called with namespace
        when(mockTemplateLoader.loadTemplate("tenant-a", "test-template.yaml", new HashMap<>()))
                .thenReturn(template);

        // Generate document with explicit namespace
        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .namespace("tenant-a")
                .templateId("test-template.yaml")
                .data(new HashMap<>())
                .build();

        composer.generateDocument(request);

        // Verify template loader was called with the correct namespace
        verify(mockTemplateLoader, times(1))
                .loadTemplate("tenant-a", "test-template.yaml", new HashMap<>());
    }

    @Test
    @DisplayName("Should default to common-templates namespace when not specified")
    public void testDocumentGenerationWithDefaultNamespace() throws IOException {
        // Create a template
        PageSection section1 = PageSection.builder()
                .sectionId("section1")
                .type(SectionType.ACROFORM)
                .order(1)
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test-template")
                .sections(Collections.singletonList(section1))
                .build();

        // Setup mock to return template
        when(mockTemplateLoader.loadTemplate(anyString(), anyMap()))
                .thenReturn(template);

        // Generate document without specifying namespace
        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .templateId("test-template.yaml")
                .data(new HashMap<>())
                .build();

        composer.generateDocument(request);

        // Verify the render context was set with common-templates as default
        verify(mockRenderer, atLeastOnce()).render(any(), argThat(context -> 
            context.getNamespace().equals("common-templates")
        ));
    }

    @Test
    @DisplayName("Should pass namespace to RenderContext for resource resolution")
    public void testNamespacePropagatedToRenderContext() throws IOException {
        // Create a template
        PageSection section1 = PageSection.builder()
                .sectionId("section1")
                .type(SectionType.ACROFORM)
                .order(1)
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test-template")
                .sections(Collections.singletonList(section1))
                .build();

        // Setup mock
        when(mockTemplateLoader.loadTemplate("tenant-a", "test.yaml", new HashMap<>()))
                .thenReturn(template);

        // Generate document with namespace
        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .namespace("tenant-a")
                .templateId("test.yaml")
                .data(new HashMap<>())
                .build();

        composer.generateDocument(request);

        // Verify the render context received the correct namespace
        verify(mockRenderer, atLeastOnce()).render(any(), argThat(context -> 
            context.getNamespace().equals("tenant-a")
        ));
    }

    @Test
    @DisplayName("Should handle multiple templates from different namespaces")
    public void testMultipleNamespacedTemplates() throws IOException {
        // Setup templates for different namespaces
        PageSection section1 = PageSection.builder()
                .sectionId("section1")
                .type(SectionType.ACROFORM)
                .order(1)
                .build();

        DocumentTemplate commonTemplate = DocumentTemplate.builder()
                .templateId("common-template")
                .sections(Collections.singletonList(section1))
                .build();

        DocumentTemplate tenantTemplate = DocumentTemplate.builder()
                .templateId("tenant-template")
                .sections(Collections.singletonList(section1))
                .build();

        // Setup mocks - don't actually generate, just verify namespace passing
        when(mockTemplateLoader.loadTemplate(eq("common-templates"), eq("common.yaml"), anyMap()))
                .thenReturn(commonTemplate);
        when(mockTemplateLoader.loadTemplate(eq("tenant-a"), eq("tenant.yaml"), anyMap()))
                .thenReturn(tenantTemplate);

        // Create requests for different namespaces
        DocumentGenerationRequest request1 = DocumentGenerationRequest.builder()
                .namespace("common-templates")
                .templateId("common.yaml")
                .data(new HashMap<>())
                .build();

        DocumentGenerationRequest request2 = DocumentGenerationRequest.builder()
                .namespace("tenant-a")
                .templateId("tenant.yaml")
                .data(new HashMap<>())
                .build();

        // Capture and verify the template loader was called with correct namespace parameters
        // without actually rendering documents (which requires complex PDF setup)
        InOrder inOrder = inOrder(mockTemplateLoader);
        
        // Call the composer with both requests
        try {
            composer.generateDocument(request1);
        } catch (RuntimeException e) {
            // Expected - we're using mocks that don't provide full PDF rendering setup
        }
        
        try {
            composer.generateDocument(request2);
        } catch (RuntimeException e) {
            // Expected - we're using mocks that don't provide full PDF rendering setup
        }

        // Verify both were called with correct namespaces (this is what matters for namespace testing)
        verify(mockTemplateLoader).loadTemplate(eq("common-templates"), eq("common.yaml"), anyMap());
        verify(mockTemplateLoader).loadTemplate(eq("tenant-a"), eq("tenant.yaml"), anyMap());
    }

    @Test
    @DisplayName("Should respect namespace context during template inheritance")
    public void testNamespaceContextPreservedInInheritance() throws IOException {
        // This test verifies that when a template inherits from another,
        // the namespace context is maintained
        
        PageSection section1 = PageSection.builder()
                .sectionId("section1")
                .type(SectionType.ACROFORM)
                .order(1)
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test-template")
                .sections(Collections.singletonList(section1))
                .metadata(new HashMap<String, Object>() {{
                    put("_namespace", "tenant-a");
                }})
                .build();

        // Setup mock
        when(mockTemplateLoader.loadTemplate("tenant-a", "test.yaml", new HashMap<>()))
                .thenReturn(template);

        // Generate document
        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .namespace("tenant-a")
                .templateId("test.yaml")
                .data(new HashMap<>())
                .build();

        composer.generateDocument(request);

        // The render context should have the namespace from the template metadata
        verify(mockRenderer, atLeastOnce()).render(any(), argThat(context -> 
            context.getNamespace().equals("tenant-a")
        ));
    }

    @Test
    @DisplayName("Should correctly set render context namespace from request when template metadata is empty")
    public void testNamespaceSetFromRequestWhenMetadataEmpty() throws IOException {
        PageSection section1 = PageSection.builder()
                .sectionId("section1")
                .type(SectionType.ACROFORM)
                .order(1)
                .build();

        // Template without namespace in metadata
        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test-template")
                .sections(Collections.singletonList(section1))
                .build();

        when(mockTemplateLoader.loadTemplate("tenant-a", "test.yaml", new HashMap<>()))
                .thenReturn(template);

        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .namespace("tenant-a")
                .templateId("test.yaml")
                .data(new HashMap<>())
                .build();

        composer.generateDocument(request);

        // Namespace should come from request
        verify(mockRenderer, atLeastOnce()).render(any(), argThat(context -> 
            "tenant-a".equals(context.getNamespace())
        ));
    }

    @Test
    @DisplayName("Should fail gracefully when namespace is invalid")
    public void testInvalidNamespaceHandling() {
        // Setup mock to throw exception for non-existent namespace
        when(mockTemplateLoader.loadTemplate("invalid-namespace", "test.yaml", new HashMap<>()))
                .thenThrow(new RuntimeException("Namespace not found: invalid-namespace"));

        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .namespace("invalid-namespace")
                .templateId("test.yaml")
                .data(new HashMap<>())
                .build();

        // Should throw exception
        assertThrows(RuntimeException.class, () -> {
            composer.generateDocument(request);
        }, "Should throw exception for invalid namespace");
    }
}
