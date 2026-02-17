package com.example.demo.docgen.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Configuration for repeating a set of field mappings for each item in a collection.
 * Useful for mapping arrays of data (like children) to numbered PDF fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepeatingGroupConfig {
    /**
     * Prefix for the PDF field name (e.g., "child" for "child1FirstName")
     */
    private String prefix;
    
    /**
     * Suffix for the PDF field name (optional)
     */
    private String suffix;
    
    /**
     * Starting index for the PDF field name (defaults to 1)
     */
    @Builder.Default
    private int startIndex = 1;
    
    /**
     * Separator between the field name and the index (e.g., "." or "_")
     */
    private String indexSeparator;
    
    /**
     * Position of the index relative to the field name
     */
    @Builder.Default
    private IndexPosition indexPosition = IndexPosition.BEFORE_FIELD;
    
    /**
     * Maximum number of items to map (optional)
     */
    private Integer maxItems;
    /**
     * Starting cell for Excel table population (e.g., "Sheet1!A5" or "A5").
     * When present, the renderer will treat this repeating group as a table
     * and will map each item's fields to columns starting at this cell.
     */
    private String startCell;

    /**
     * When true, existing rows at the insertion point will be shifted down
     * to make space for the populated rows. Defaults to false.
     */
    private Boolean insertRows;

    /**
     * When true, cell values will overwrite existing cells; when false, existing
     * values will be preserved if present. Defaults to true.
     */
    @Builder.Default
    private Boolean overwrite = true;
    
    /**
     * Field mappings for a single item.
     * The PDF field name construction depends on indexPosition:
     * - BEFORE_FIELD: prefix + index + indexSeparator + fieldKey + suffix
     * - AFTER_FIELD: prefix + fieldKey + indexSeparator + index + suffix
     */
    private Map<String, String> fields;

    public enum IndexPosition {
        BEFORE_FIELD,
        AFTER_FIELD
    }
}
