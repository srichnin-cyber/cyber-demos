package com.example.demo.docgen.renderer;

import com.example.demo.docgen.aspect.LogExecutionTime;
import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.exception.ResourceLoadingException;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.model.FieldMappingGroup;
import com.example.demo.docgen.model.FieldStyling;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.SectionType;
import com.example.demo.docgen.service.NamespaceResolver;
import com.example.demo.docgen.service.ResourceStorageClient;
import com.example.demo.docgen.service.TemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;

/**
 * Renderer for AcroForm PDF templates
 * Fills form fields with data from the context using configurable mapping strategies
 * Supports both single mapping strategy and multiple mapping groups
 * Supports namespace-aware template loading
 */
@Slf4j
@Component
public class AcroFormRenderer implements SectionRenderer {
    
    private final List<FieldMappingStrategy> mappingStrategies;
    private final TemplateLoader templateLoader;
    private final NamespaceResolver namespaceResolver;
    private ResourceStorageClient resourceStorageClient;
    
    @Autowired
    public AcroFormRenderer(List<FieldMappingStrategy> mappingStrategies, TemplateLoader templateLoader, NamespaceResolver namespaceResolver) {
        this.mappingStrategies = mappingStrategies;
        this.templateLoader = templateLoader;
        this.namespaceResolver = namespaceResolver;
    }

    @Autowired(required = false)
    public void setResourceStorageClient(ResourceStorageClient resourceStorageClient) {
        this.resourceStorageClient = resourceStorageClient;
    }

    // Backwards-compatible constructor for tests and legacy callers
    public AcroFormRenderer(List<FieldMappingStrategy> mappingStrategies, TemplateLoader templateLoader) {
        this(mappingStrategies, templateLoader, new NamespaceResolver());
    }
    
    @Override
    @LogExecutionTime("AcroForm Rendering")
    public PDDocument render(PageSection section, RenderContext context) {
        try {
            // Load the PDF form template with namespace support
            PDDocument document = loadTemplate(section.getTemplatePath(), context);
            
            // Get the form
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                log.warn("No AcroForm found in template: {}", section.getTemplatePath());
                return document;
            }
            
            // Map data to field values (supports both single and multiple groups)
            Map<String, String> fieldValues = mapFieldValues(section, context);
            log.info("Mapped field values: {}", fieldValues);
            
            // Fill form fields with values and apply styles
            fillFormFields(acroForm, fieldValues, section);
            
            // Flatten the form to make it read-only (optional)
            // acroForm.flatten();
            
            return document;
            
        } catch (ResourceLoadingException rle) {
            // Propagate resource loading errors so controller can return user-friendly response
            throw rle;
        } catch (IOException e) {
            throw new RuntimeException("Failed to render AcroForm section: " + section.getSectionId(), e);
        }
    }
    
    @Override
    public boolean supports(SectionType type) {
        return type == SectionType.ACROFORM;
    }
    
    /**
     * Map field values using either single strategy or multiple groups
     */
    private Map<String, String> mapFieldValues(PageSection section, RenderContext context) {
        if (section.hasMultipleMappingGroups()) {
            return mapWithMultipleGroups(section, context);
        } else {
            return mapWithSingleStrategy(section, context);
        }
    }
    
    /**
     * Traditional single-strategy mapping (backward compatible)
     */
    private Map<String, String> mapWithSingleStrategy(PageSection section, RenderContext context) {
        log.info("Rendering AcroForm section: {} with single mapping type: {}", 
                section.getSectionId(), section.getMappingType());
        
        FieldMappingStrategy strategy = findMappingStrategy(section.getMappingType());
        return strategy.mapData(context.getData(), section.getFieldMappings());
    }
    
    /**
     * New multi-group mapping - merges results from multiple strategies
     * Supports basePath optimization to avoid repeated filter executions
     */
    private Map<String, String> mapWithMultipleGroups(PageSection section, RenderContext context) {
        log.info("Rendering AcroForm section: {} with {} mapping groups", 
                section.getSectionId(), section.getFieldMappingGroups().size());
        
        Map<String, String> allFieldValues = new HashMap<>();
        
        for (FieldMappingGroup group : section.getFieldMappingGroups()) {
            log.debug("Processing mapping group with type: {}, fields: {}, basePath: {}", 
                     group.getMappingType(), group.getFields().size(), group.getBasePath());
            
            FieldMappingStrategy strategy = findMappingStrategy(group.getMappingType());
            
            Map<String, String> groupValues;
            if (group.getRepeatingGroup() != null) {
                // Handle repeating group (e.g., children)
                groupValues = mapRepeatingGroup(group, context, strategy);
            } else if (group.getBasePath() != null && !group.getBasePath().isEmpty()) {
                // Optimize: evaluate basePath ONCE, then map fields relative to that result
                groupValues = strategy.mapDataWithBasePath(
                    context.getData(), 
                    group.getBasePath(), 
                    group.getFields()
                );
            } else {
                // Standard mapping without basePath optimization
                groupValues = strategy.mapData(context.getData(), group.getFields());
            }
            
            // Merge into results (later groups override earlier ones for same field)
            allFieldValues.putAll(groupValues);
            
            log.debug("Mapped {} fields using {} strategy", 
                     groupValues.size(), group.getMappingType());
        }
        
        log.info("Total fields mapped: {}", allFieldValues.size());
        return allFieldValues;
    }

    /**
     * Maps a repeating group of data (e.g., an array of children) to numbered PDF fields.
     */
    private Map<String, String> mapRepeatingGroup(FieldMappingGroup group, RenderContext context, FieldMappingStrategy strategy) {
        Map<String, String> result = new HashMap<>();
        com.example.demo.docgen.model.RepeatingGroupConfig config = group.getRepeatingGroup();
        
        if (group.getBasePath() == null || group.getBasePath().isEmpty()) {
            log.warn("Repeating group specified without basePath in section {}", group);
            return result;
        }
        
        // 1. Evaluate basePath to get the collection
        Object collection = strategy.evaluatePath(context.getData(), group.getBasePath());
        
        if (!(collection instanceof List)) {
            log.warn("Repeating group basePath '{}' did not evaluate to a List. Got: {}", 
                    group.getBasePath(), collection != null ? collection.getClass().getName() : "null");
            return result;
        }
        
        List<?> items = (List<?>) collection;
        int startIndex = config.getStartIndex();
        int maxItems = config.getMaxItems() != null ? config.getMaxItems() : items.size();
        int count = Math.min(items.size(), maxItems);
        
        log.debug("Mapping repeating group with {} items (max: {})", count, maxItems);
        
        // 2. Iterate over items and map fields
        for (int i = 0; i < count; i++) {
            Object item = items.get(i);
            int displayIndex = startIndex + i;
            
            // Construct PDF field names for this item
            Map<String, String> itemFieldMappings = new HashMap<>();
            for (Map.Entry<String, String> fieldEntry : config.getFields().entrySet()) {
                String baseFieldName = fieldEntry.getKey();
                String dataPath = fieldEntry.getValue();
                
                // Construct PDF field name based on position and separator
                StringBuilder pdfFieldName = new StringBuilder();
                if (config.getPrefix() != null) pdfFieldName.append(config.getPrefix());
                
                if (config.getIndexPosition() == com.example.demo.docgen.model.RepeatingGroupConfig.IndexPosition.BEFORE_FIELD) {
                    pdfFieldName.append(displayIndex);
                    if (config.getIndexSeparator() != null) pdfFieldName.append(config.getIndexSeparator());
                    pdfFieldName.append(baseFieldName);
                } else {
                    pdfFieldName.append(baseFieldName);
                    if (config.getIndexSeparator() != null) pdfFieldName.append(config.getIndexSeparator());
                    pdfFieldName.append(displayIndex);
                }
                
                if (config.getSuffix() != null) pdfFieldName.append(config.getSuffix());
                
                itemFieldMappings.put(pdfFieldName.toString(), dataPath);
            }
            
            // Map fields for this single item
            Map<String, String> itemValues = strategy.mapFromContext(item, itemFieldMappings);
            result.putAll(itemValues);
        }
        
        return result;
    }
    
    private FieldMappingStrategy findMappingStrategy(com.example.demo.docgen.mapper.MappingType mappingType) {
        return mappingStrategies.stream()
            .filter(strategy -> strategy.supports(mappingType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No mapping strategy found for type: " + mappingType));
    }
    
    @LogExecutionTime("Loading PDF Template")
    private PDDocument loadTemplate(String templatePath, RenderContext context) throws IOException, ResourceLoadingException {
        // Resolve namespace-aware template path
        // If the section didn't include a templatePath, attempt fallbacks from the loaded template
        String effectiveTemplatePath = templatePath;
        if (effectiveTemplatePath == null && context != null && context.getTemplate() != null) {
            // 1) Check explicit section overrides
            if (context.getTemplate().getSectionOverrides() != null) {
                String override = context.getTemplate().getSectionOverrides().get(context.getCurrentSectionId());
                if (override != null && !override.isEmpty()) {
                    effectiveTemplatePath = override;
                }
            }

            // 2) As a last resort, try to find the section entry in the template and use its templatePath
            if (effectiveTemplatePath == null && context.getTemplate().getSections() != null) {
                for (com.example.demo.docgen.model.PageSection s : context.getTemplate().getSections()) {
                    if (s != null && s.getSectionId() != null && s.getSectionId().equals(context.getCurrentSectionId())) {
                        if (s.getTemplatePath() != null && !s.getTemplatePath().isEmpty()) {
                            effectiveTemplatePath = s.getTemplatePath();
                            break;
                        }
                    }
                }
            }
        }

        String resolvedPath = namespaceResolver.resolveResourcePath(effectiveTemplatePath, context.getNamespace());

        if (resolvedPath == null) {
            throw new ResourceLoadingException(
                "RESOURCE_RESOLUTION_FAILED",
                "Failed to resolve PDF template path: " + effectiveTemplatePath + " in namespace: " + context.getNamespace()
            );
        }

        log.debug("Resolved PDF template path: {} -> {}", effectiveTemplatePath, resolvedPath);

        // Attempt external resource storage first (if configured), then fall back to TemplateLoader
        if (resourceStorageClient != null && resourceStorageClient.isEnabled()) {
            String storageNamespace = namespaceResolver.extractNamespaceFromPath(resolvedPath);
            String relativePath;
            String prefix = storageNamespace + "/templates/";
            if (resolvedPath.startsWith(prefix)) {
                relativePath = resolvedPath.substring(prefix.length());
            } else {
                int idx = resolvedPath.indexOf("/templates/");
                if (idx >= 0) {
                    relativePath = resolvedPath.substring(idx + "/templates/".length());
                } else {
                    relativePath = resolvedPath;
                }
            }

            try {
                byte[] pdfBytes = resourceStorageClient.getResourceBytes(storageNamespace, relativePath);
                return PDDocument.load(pdfBytes);
            } catch (IOException e) {
                log.warn("External resource storage fetch failed for {}/{} - falling back to TemplateLoader: {}",
                        storageNamespace, relativePath, e.getMessage());
                // fall-through to templateLoader fallback
            }
        }

        try {
            byte[] pdfBytes = templateLoader.getResourceBytes(resolvedPath);
            return PDDocument.load(pdfBytes);
        } catch (com.example.demo.docgen.exception.TemplateLoadingException tle) {
            Throwable cause = tle.getCause() != null ? tle.getCause() : tle;
            throw new ResourceLoadingException(
                "RESOURCE_NOT_FOUND",
                "PDF template resource '" + templatePath + "' not found. Resolved path: " + resolvedPath + ". Check classpath or Config Server.",
                cause
            );
        } catch (IOException e) {
            log.error("Failed to load PDF template resource: {}", resolvedPath, e);
            throw new ResourceLoadingException(
                "RESOURCE_NOT_FOUND",
                "PDF template resource '" + templatePath + "' not found. Resolved path: " + resolvedPath + ". Check classpath or Config Server.",
                e
            );
        }
    }
    
    @LogExecutionTime("Filling AcroForm Fields")
    private void fillFormFields(PDAcroForm acroForm, Map<String, String> fieldValues, PageSection section) {
        // Collect all field styles from all mapping groups
        Map<String, FieldStyling> allFieldStyles = new HashMap<>();
        
        if (section.hasMultipleMappingGroups()) {
            for (FieldMappingGroup group : section.getFieldMappingGroups()) {
                if (group.getFieldStyles() != null && !group.getFieldStyles().isEmpty()) {
                    allFieldStyles.putAll(group.getFieldStyles());
                }
            }
        }
        
        for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();
            
            try {
                PDField field = acroForm.getField(fieldName);
                if (field != null) {
                    field.setValue(value);
                    log.debug("Set field '{}' = '{}'", fieldName, value);
                    
                    // Apply styling if configured
                    if (allFieldStyles.containsKey(fieldName)) {
                        applyFieldStyling(acroForm, field, allFieldStyles.get(fieldName));
                    }
                } else {
                    log.warn("Field not found in form: {}", fieldName);
                }
            } catch (Exception e) {
                log.error("Failed to set field '{}': {}", fieldName, e.getMessage());
            }
        }
    }
    
    /**
     * Apply styling to an AcroForm field
     * Supports font size, colors, alignment, and field properties
     */
    private void applyFieldStyling(PDAcroForm acroForm, PDField field, FieldStyling styling) {
        if (styling == null) {
            return;
        }
        
        try {
            // Ensure AcroForm default resources exist
            PDResources resources = acroForm.getDefaultResources();
            if (resources == null) {
                resources = new PDResources();
                acroForm.setDefaultResources(resources);
            }

            // Choose a PDType1Font based on requested fontName / bold / italic
            PDFont pdfFont;
            String fontName = styling.getFontName() != null ? styling.getFontName().toLowerCase() : "helvetica";
            boolean bold = styling.getBold() != null && styling.getBold();
            if (fontName.contains("times")) {
                pdfFont = bold ? PDType1Font.TIMES_BOLD : PDType1Font.TIMES_ROMAN;
            } else if (fontName.contains("courier")) {
                pdfFont = bold ? PDType1Font.COURIER_BOLD : PDType1Font.COURIER;
            } else {
                // default to Helvetica
                pdfFont = bold ? PDType1Font.HELVETICA_BOLD : PDType1Font.HELVETICA;
            }

            // Register font in AcroForm resources and build default appearance (DA)
            org.apache.pdfbox.cos.COSName fontResName = resources.add(pdfFont);
            StringBuilder da = new StringBuilder();
            da.append('/').append(fontResName.getName()).append(' ');
            if (styling.getFontSize() != null) {
                da.append(styling.getFontSize());
            } else {
                da.append("0");
            }
            da.append(" Tf ");

            // Apply text color (append to DA)
            if (styling.getTextColor() != null) {
                float r = ((styling.getTextColor() >> 16) & 0xFF) / 255.0f;
                float g = ((styling.getTextColor() >> 8) & 0xFF) / 255.0f;
                float b = (styling.getTextColor() & 0xFF) / 255.0f;
                da.append(String.format("%s %s %s rg", stripTrailingZeros(r), stripTrailingZeros(g), stripTrailingZeros(b)));
                log.debug("Applied text color [R:{}, G:{}, B:{}] to field: {}", r, g, b, field.getFullyQualifiedName());
            } else {
                // default black
                da.append("0 0 0 rg");
            }

            // Set the default appearance on the field (DA)
            field.getCOSObject().setString(org.apache.pdfbox.cos.COSName.DA, da.toString());

            // Apply background color using COSArray
            if (styling.getBackgroundColor() != null) {
                float r = ((styling.getBackgroundColor() >> 16) & 0xFF) / 255.0f;
                float g = ((styling.getBackgroundColor() >> 8) & 0xFF) / 255.0f;
                float b = (styling.getBackgroundColor() & 0xFF) / 255.0f;
                
                COSArray bgColor = new COSArray();
                bgColor.add(new COSFloat(r));
                bgColor.add(new COSFloat(g));
                bgColor.add(new COSFloat(b));
                field.getCOSObject().setItem(COSName.getPDFName("BG"), bgColor);
                log.debug("Applied background color to field: {}", field.getFullyQualifiedName());
            }
            
            // Apply border styling
            if (styling.getBorderColor() != null) {
                float r = ((styling.getBorderColor() >> 16) & 0xFF) / 255.0f;
                float g = ((styling.getBorderColor() >> 8) & 0xFF) / 255.0f;
                float b = (styling.getBorderColor() & 0xFF) / 255.0f;
                
                COSArray borderColor = new COSArray();
                borderColor.add(new COSFloat(r));
                borderColor.add(new COSFloat(g));
                borderColor.add(new COSFloat(b));
                field.getCOSObject().setItem(COSName.getPDFName("BC"), borderColor);
                log.debug("Applied border color to field: {}", field.getFullyQualifiedName());
            }
            
            if (styling.getBorderWidth() != null) {
                // Border width is typically part of the BS (Border Style) dictionary
                // For simplicity, we'll log it as a note
                log.debug("Border width styling ({}pt) requires BS dictionary setup", styling.getBorderWidth());
            }
            
            // Apply text alignment
            if (styling.getAlignment() != null) {
                field.getCOSObject().setItem(COSName.getPDFName("Q"), new COSFloat(styling.getAlignment().code));
                log.debug("Applied text alignment {} to field: {}", styling.getAlignment(), field.getFullyQualifiedName());
            }
            
            // Apply field flags (read-only, hidden, etc.)
            int currentFlags = field.getCOSObject().getInt(COSName.FF, 0);
            
            if (styling.getReadOnly() != null && styling.getReadOnly()) {
                currentFlags |= 1; // Set bit 0 (ReadOnly)
                log.debug("Applied read-only flag to field: {}", field.getFullyQualifiedName());
            }
            
            if (styling.getHidden() != null && styling.getHidden()) {
                currentFlags |= (1 << 12); // Set bit 12 (Password)
                log.debug("Applied hidden flag to field: {}", field.getFullyQualifiedName());
            }
            
            if (currentFlags != 0) {
                field.getCOSObject().setItem(COSName.FF, new COSFloat(currentFlags));
            }
            
        } catch (Exception e) {
            log.error("Failed to apply styling to field {}: {}", field.getFullyQualifiedName(), e.getMessage());
        }
    }

    // Helper to format floats without scientific notation and strip trailing zeros
    private static String stripTrailingZeros(float v) {
        String s = Float.toString(v);
        if (s.indexOf('E') >= 0 || s.indexOf('e') >= 0) {
            s = new java.math.BigDecimal(Float.toString(v)).toPlainString();
        }
        // remove trailing zeros
        if (s.indexOf('.') >= 0) {
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}

