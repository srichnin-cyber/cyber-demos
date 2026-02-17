package com.example.demo.docgen.service;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.exception.ResourceLoadingException;
import com.example.demo.docgen.exception.TemplateLoadingException;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.mapper.JsonPathMappingStrategy;
import com.example.demo.docgen.mapper.MappingType;
import com.example.demo.docgen.model.DocumentGenerationRequest;
import com.example.demo.docgen.model.DocumentTemplate;
import com.example.demo.docgen.model.OverflowConfig;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.example.demo.docgen.renderer.SectionRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main orchestrator for document generation
 * Coordinates template loading, section rendering, and PDF assembly
 *
 * Notes:
 * - This class is intentionally thin: it coordinates the template loader,
 *   renderers and post-processing (header/footer, merges) but delegates the
 *   heavy work to `TemplateLoader` and `SectionRenderer` implementations.
 * - Keep this orchestration logic deterministic and side-effect free when
 *   possible to make unit testing simpler (renderers can be mocked).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentComposer {
    private final List<SectionRenderer> renderers;
    private final List<FieldMappingStrategy> mappingStrategies;
    private final TemplateLoader templateLoader;
    private final com.example.demo.docgen.processor.HeaderFooterProcessor headerFooterProcessor;
    private final com.example.demo.docgen.service.ExcelOutputService excelOutputService;
    
    /**
     * Generate a PDF document from a template and data
     *
     * @param request Generation request with template ID and data
     * @return PDF document as byte array
     */
    @LogExecutionTime("Total Document Generation")
    public byte[] generateDocument(DocumentGenerationRequest request) {
        log.info("Generating document with template: {} from namespace: {}", request.getTemplateId(), request.getNamespace());
        
        try {
            // Load template structure with namespace support. TemplateLoader is
            // responsible for inheritance, fragment resolution and placeholder
            // interpolation. Errors at this stage are TemplateLoadingException.
            DocumentTemplate template;
            if (request.getNamespace() != null) {
                template = templateLoader.loadTemplate(request.getNamespace(), request.getTemplateId(), request.getData());
            } else {
                // Backwards-compatible call used by unit tests and legacy callers
                template = templateLoader.loadTemplate(request.getTemplateId(), request.getData());
            }

            // Build a RenderContext that carries the template and the runtime data
            // to the SectionRenderers. The context is the single source of truth
            // for renderer evaluation (mapping, conditions, data lookups).
            RenderContext context = new RenderContext(template, request.getData());
            String ns = request.getNamespace() != null ? request.getNamespace() : "common-templates";
            context.setNamespace(ns);
            
            // Render each section into an in-memory PDDocument. Sections are
            // processed in order; each SectionRenderer returns a PDDocument
            // representing that section's PDF content.
            List<PDDocument> sectionDocuments = new ArrayList<>();
            List<PageSection> sections = new ArrayList<>(template.getSections());
            sections.sort(Comparator.comparingInt(PageSection::getOrder));
            
            for (PageSection section : sections) {
                // Evaluate condition if present
                if (section.getCondition() != null && !section.getCondition().isEmpty()) {
                    FieldMappingStrategy strategy = findMappingStrategy(section.getMappingType());
                    Object result = strategy.evaluatePath(context.getData(), section.getCondition());
                    
                    boolean shouldRender = false;
                    if (result instanceof Boolean) {
                        shouldRender = (Boolean) result;
                    } else if (result != null) {
                        // If it's not a boolean, we consider it true if it's not null/empty
                        shouldRender = !result.toString().isEmpty() && !result.toString().equalsIgnoreCase("false");
                    }
                    
                    if (!shouldRender) {
                        log.info("Skipping section {} due to condition: {}", section.getSectionId(), section.getCondition());
                        continue;
                    }
                }

                log.info("Rendering section: {} (type: {})", section.getSectionId(), section.getType());
                context.setCurrentSectionId(section.getSectionId());
                
                SectionRenderer renderer = findRenderer(section.getType());
                PDDocument sectionDoc = renderer.render(section, context);
                sectionDocuments.add(sectionDoc);

                // Handle overflows if configured
                if (section.getOverflowConfigs() != null && !section.getOverflowConfigs().isEmpty()) {
                    for (OverflowConfig config : section.getOverflowConfigs()) {
                        List<PDDocument> overflowDocs = handleOverflow(section, config, context);
                        sectionDocuments.addAll(overflowDocs);
                    }
                }
            }
            
            // Merge all generated section PDFs into a single PDDocument. Note:
            // - When there is exactly one section the merger returns that document
            //   directly (no copy), so closing behavior must avoid double-closing.
            // - For merged outputs we create a new PDDocument and append all
            //   sections into it which should then be saved and closed.
            PDDocument mergedDocument = mergeSections(sectionDocuments);
            
            // Apply headers and footers using the dedicated processor which will
            // iterate pages and stamp content. This mutates `mergedDocument`.
            headerFooterProcessor.apply(
                mergedDocument,
                template.getHeaderFooterConfig(),
                request.getData()
            );
            
            // Serialize to bytes for transport/storage
            byte[] pdfBytes = convertToBytes(mergedDocument);
            
            // Cleanup - close documents and handle merged document separately to avoid double-closing
            // Close all section documents first. Section renderers may return the
            // same PDDocument instance that `mergedDocument` points to in the
            // single-section case; hence the explicit size check below.
            for (PDDocument doc : sectionDocuments) {
                try {
                    if (doc != null) {
                        doc.close();
                    }
                } catch (IOException e) {
                    log.warn("Error closing section document", e);
                }
            }
            
            // Only close merged document if it's not one of the section documents (would be double-closed)
            if (sectionDocuments.size() != 1) {
                try {
                    if (mergedDocument != null) {
                        mergedDocument.close();
                    }
                } catch (IOException e) {
                    log.warn("Error closing merged document", e);
                }
            }
            
            log.info("Document generation complete. Size: {} bytes", pdfBytes.length);
            return pdfBytes;
            
        } catch (TemplateLoadingException tle) {
            // Propagate template loading exceptions so controller can return user-friendly response
            log.error("Template resolution error", tle);
            throw tle;
        } catch (ResourceLoadingException rle) {
            // Propagate resource loading exceptions so controller can return user-friendly response
            log.error("Resource loading error", rle);
            throw rle;
        } catch (Exception e) {
            log.error("Document generation failed", e);
            throw new RuntimeException("Failed to generate document", e);
        }
    }

    /**
     * Generate an Excel workbook from a template and data and return XLSX bytes.
     * The ExcelSectionRenderer stores the filled workbook in the RenderContext metadata under "excelWorkbook".
     */
    @LogExecutionTime("Total Excel Generation")
    public byte[] generateExcel(DocumentGenerationRequest request) {
        log.info("Generating EXCEL with template: {} from namespace: {}", request.getTemplateId(), request.getNamespace());

        try {
            DocumentTemplate template;
            if (request.getNamespace() != null) {
                template = templateLoader.loadTemplate(request.getNamespace(), request.getTemplateId(), request.getData());
            } else {
                template = templateLoader.loadTemplate(request.getTemplateId(), request.getData());
            }

            RenderContext context = new RenderContext(template, request.getData());
            String ns = request.getNamespace() != null ? request.getNamespace() : "common-templates";
            context.setNamespace(ns);

            List<PageSection> sections = new ArrayList<>(template.getSections());
            sections.sort(Comparator.comparingInt(PageSection::getOrder));

            for (PageSection section : sections) {
                if (section.getCondition() != null && !section.getCondition().isEmpty()) {
                    FieldMappingStrategy strategy = findMappingStrategy(section.getMappingType());
                    Object result = strategy.evaluatePath(context.getData(), section.getCondition());
                    boolean shouldRender = false;
                    if (result instanceof Boolean) {
                        shouldRender = (Boolean) result;
                    } else if (result != null) {
                        shouldRender = !result.toString().isEmpty() && !result.toString().equalsIgnoreCase("false");
                    }
                    if (!shouldRender) continue;
                }

                context.setCurrentSectionId(section.getSectionId());
                SectionRenderer renderer = findRenderer(section.getType());
                // Render; ExcelSectionRenderer stores workbook in context metadata
                renderer.render(section, context);
            }

            Object wbObj = context.getMetadata("excelWorkbook");
            if (wbObj == null || !(wbObj instanceof org.apache.poi.ss.usermodel.Workbook)) {
                throw new RuntimeException("No Excel workbook produced by renderers");
            }

            org.apache.poi.ss.usermodel.Workbook workbook = (org.apache.poi.ss.usermodel.Workbook) wbObj;
            return excelOutputService.toBytes(workbook);

        } catch (TemplateLoadingException | ResourceLoadingException e) {
            log.error("Template/resource error during Excel generation", e);
            throw e;
        } catch (Exception e) {
            log.error("Excel generation failed", e);
            throw new RuntimeException("Failed to generate Excel", e);
        }
    }

    /**
     * Handles data overflow by generating addendum pages
     */
    private List<PDDocument> handleOverflow(PageSection section, OverflowConfig config, RenderContext context) throws IOException {
        List<PDDocument> overflowDocs = new ArrayList<>();
        
        if (config.getArrayPath() == null || config.getAddendumTemplatePath() == null) {
            return overflowDocs;
        }

        // 1. Evaluate arrayPath using the specified strategy
        FieldMappingStrategy strategy = findMappingStrategy(config.getMappingType());
        Object collection = strategy.evaluatePath(context.getData(), config.getArrayPath());
        
        if (!(collection instanceof List)) {
            log.debug("Overflow arrayPath '{}' did not evaluate to a List", config.getArrayPath());
            return overflowDocs;
        }
        
        List<?> allItems = (List<?>) collection;
        if (allItems.size() <= config.getMaxItemsInMain()) {
            log.debug("No overflow detected for section {}. Items: {}, Max: {}", 
                     section.getSectionId(), allItems.size(), config.getMaxItemsInMain());
            return overflowDocs;
        }
        
        log.info("Overflow detected for section {}. Total items: {}, Max in main: {}", 
                 section.getSectionId(), allItems.size(), config.getMaxItemsInMain());
        
        // 2. Get overflow items
        List<?> overflowItems = allItems.subList(config.getMaxItemsInMain(), allItems.size());
        
        // 3. Partition into pages and render
        int pageSize = config.getItemsPerOverflowPage() > 0 ? config.getItemsPerOverflowPage() : overflowItems.size();
        for (int i = 0; i < overflowItems.size(); i += pageSize) {
            List<?> chunk = overflowItems.subList(i, Math.min(i + pageSize, overflowItems.size()));
            int pageNum = (i / pageSize) + 1;
            
            log.info("Rendering addendum page {} for section {} with {} items", 
                     pageNum, section.getSectionId(), chunk.size());

            // Create a temporary section for the addendum
            PageSection addendumSection = PageSection.builder()
                .sectionId(section.getSectionId() + "_addendum_" + pageNum)
                .type(SectionType.FREEMARKER)
                .templatePath(config.getAddendumTemplatePath())
                .build();
            
            // Create a temporary data map for this addendum page
            Map<String, Object> addendumData = new HashMap<>(context.getData());
            addendumData.put("overflowItems", chunk);
            addendumData.put("isAddendum", true);
            addendumData.put("addendumPageNumber", pageNum);
            addendumData.put("totalAddendumPages", (int) Math.ceil((double) overflowItems.size() / pageSize));
            
            RenderContext addendumContext = new RenderContext(context.getTemplate(), addendumData);
            
            SectionRenderer renderer = findRenderer(SectionType.FREEMARKER);
            overflowDocs.add(renderer.render(addendumSection, addendumContext));
        }
        
        return overflowDocs;
    }

    private FieldMappingStrategy findMappingStrategy(MappingType type) {
        return mappingStrategies.stream()
            .filter(s -> s.supports(type))
            .findFirst()
            .orElseThrow(() -> new UnsupportedOperationException("No strategy found for type: " + type));
    }
    
    private SectionRenderer findRenderer(com.example.demo.docgen.model.SectionType type) {
        return renderers.stream()
            .filter(r -> r.supports(type))
            .findFirst()
            .orElseThrow(() -> new UnsupportedOperationException(
                "No renderer found for section type: " + type));
    }
    
    @LogExecutionTime("Merging PDF Sections")
    private PDDocument mergeSections(List<PDDocument> sections) throws IOException {
        if (sections.isEmpty()) {
            return new PDDocument();
        }
        
        if (sections.size() == 1) {
            return sections.get(0);
        }
        
        PDDocument result = new PDDocument();
        PDFMergerUtility merger = new PDFMergerUtility();
        
        for (PDDocument section : sections) {
            merger.appendDocument(result, section);
        }
        
        return result;
    }
    
    private byte[] convertToBytes(PDDocument document) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.save(baos);
        return baos.toByteArray();
    }
}
