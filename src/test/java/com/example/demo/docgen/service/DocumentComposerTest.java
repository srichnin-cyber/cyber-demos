package com.example.demo.docgen.service;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.mapper.JsonPathMappingStrategy;
import com.example.demo.docgen.model.*;
import com.example.demo.docgen.renderer.SectionRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocumentComposerTest {

    private DocumentComposer composer;
    private SectionRenderer mockRenderer;
    private TemplateLoader mockTemplateLoader;
    private com.example.demo.docgen.processor.HeaderFooterProcessor mockHeaderFooterProcessor;
        private com.example.demo.docgen.service.ExcelOutputService mockExcelOutputService;

    @BeforeEach
    public void setup() {
        mockRenderer = mock(SectionRenderer.class);
        mockTemplateLoader = mock(TemplateLoader.class);
        mockHeaderFooterProcessor = mock(com.example.demo.docgen.processor.HeaderFooterProcessor.class);
        
        List<SectionRenderer> renderers = Collections.singletonList(mockRenderer);
        List<FieldMappingStrategy> strategies = Collections.singletonList(new JsonPathMappingStrategy());
        
        mockExcelOutputService = mock(com.example.demo.docgen.service.ExcelOutputService.class);
        composer = new DocumentComposer(renderers, strategies, mockTemplateLoader, mockHeaderFooterProcessor, mockExcelOutputService);
        
        when(mockRenderer.supports(any())).thenReturn(true);
        try {
            when(mockRenderer.render(any(), any())).thenReturn(new PDDocument());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testConditionalRendering() throws IOException {
        // Setup template with two sections, one with a condition that fails
        PageSection s1 = PageSection.builder()
                .sectionId("s1")
                .type(SectionType.ACROFORM)
                .condition("$.showS1 == true")
                .order(1)
                .build();
        
        PageSection s2 = PageSection.builder()
                .sectionId("s2")
                .type(SectionType.ACROFORM)
                .condition("$.showS2 == true")
                .order(2)
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test")
                .sections(Arrays.asList(s1, s2))
                .build();

        when(mockTemplateLoader.loadTemplate(anyString())).thenReturn(template);
        when(mockTemplateLoader.loadTemplate(anyString(), anyMap())).thenReturn(template);

        // Data where only s2 should show
        Map<String, Object> data = new HashMap<>();
        data.put("showS1", false);
        data.put("showS2", true);

        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .templateId("test")
                .data(data)
                .build();
        composer.generateDocument(request);

        // Verify s1 was skipped and s2 was rendered
        verify(mockRenderer, never()).render(eq(s1), any());
        verify(mockRenderer, times(1)).render(eq(s2), any());
    }

    @Test
    public void testOverflowHandling() throws IOException {
        // Setup section with overflow
        OverflowConfig overflow = OverflowConfig.builder()
                .arrayPath("$.items")
                .maxItemsInMain(2)
                .itemsPerOverflowPage(2)
                .addendumTemplatePath("addendum.ftl")
                .build();

        PageSection s1 = PageSection.builder()
                .sectionId("main")
                .type(SectionType.ACROFORM)
                .overflowConfigs(Collections.singletonList(overflow))
                .order(1)
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("test")
                .sections(Collections.singletonList(s1))
                .build();

        when(mockTemplateLoader.loadTemplate(anyString())).thenReturn(template);
        when(mockTemplateLoader.loadTemplate(anyString(), anyMap())).thenReturn(template);

        // Data with 5 items (2 in main, 2 in addendum 1, 1 in addendum 2)
        Map<String, Object> data = new HashMap<>();
        data.put("items", Arrays.asList("a", "b", "c", "d", "e"));

        DocumentGenerationRequest request = DocumentGenerationRequest.builder()
                .templateId("test")
                .data(data)
                .build();
        composer.generateDocument(request);

        // Verify main section rendered once
        verify(mockRenderer, times(1)).render(eq(s1), any());
        
        // Verify addendum pages rendered (2 more times for the 3 overflow items)
        // The addendum sections are created dynamically with type FREEMARKER
        verify(mockRenderer, times(2)).render(argThat(s -> s.getSectionId().contains("addendum")), any());
    }
}
