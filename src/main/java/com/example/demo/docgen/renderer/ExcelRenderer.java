package com.example.demo.docgen.renderer;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.model.PageSection;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Optional interface for renderers that can produce an Apache POI Workbook
 * directly (used for Excel output path).
 */
public interface ExcelRenderer {
    /**
     * Render the given section into a POI Workbook. Implementations should
     * store any necessary metadata on the provided RenderContext if required.
     */
    Workbook renderWorkbook(PageSection section, RenderContext context);
}
