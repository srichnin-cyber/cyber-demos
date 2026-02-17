package com.example.demo.docgen.renderer;

import com.example.demo.docgen.core.RenderContext;
import com.example.demo.docgen.mapper.FieldMappingStrategy;
import com.example.demo.docgen.mapper.JsonPathMappingStrategy;
import com.example.demo.docgen.mapper.MappingType;
import com.example.demo.docgen.model.PageSection;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ExcelSectionRendererTest {

    private ExcelSectionRenderer renderer;
    private com.example.demo.docgen.service.TemplateLoader templateLoader;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setup() {
        templateLoader = Mockito.mock(com.example.demo.docgen.service.TemplateLoader.class);
        List<FieldMappingStrategy> strategies = Collections.singletonList(new JsonPathMappingStrategy());
        renderer = new ExcelSectionRenderer(strategies, templateLoader);
    }

    /**
     * Test basic Excel cell filling with JSONPATH mapping
     */
    @Test
    public void testBasicExcelCellFilling() throws Exception {
        // Create a test Excel template
        Path templatePath = createTestExcelTemplate("test-template.xlsx");
        byte[] templateBytes = readFileAsBytes(templatePath);

        // Setup mock to return template bytes
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Setup test data
        Map<String, Object> data = new HashMap<>();
        data.put("firstName", "John");
        data.put("lastName", "Doe");
        data.put("email", "john@example.com");

        // Setup section with JSONPATH mappings
        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("A1", "$.firstName");
        fieldMappings.put("B1", "$.lastName");
        fieldMappings.put("C1", "$.email");

        PageSection section = PageSection.builder()
                .sectionId("basic_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("test-template.xlsx")
                .mappingType(MappingType.JSONPATH)
                .fieldMappings(fieldMappings)
                .build();

        RenderContext context = new RenderContext(null, data);
        context.setNamespace("common-templates");

        // Execute - should not throw
        renderer.render(section, context);

        // Verify workbook was saved to context
        Workbook workbook = (Workbook) context.getMetadata("excelWorkbook");
        assertNotNull(workbook, "Workbook should be stored in context metadata");

        // Verify values were written
        Sheet sheet = workbook.getSheetAt(0);
        assertEquals("John", sheet.getRow(0).getCell(0).getStringCellValue(), "Cell A1 should contain 'John'");
        assertEquals("Doe", sheet.getRow(0).getCell(1).getStringCellValue(), "Cell B1 should contain 'Doe'");
        assertEquals("john@example.com", sheet.getRow(0).getCell(2).getStringCellValue(), "Cell C1 should contain email");
    }

    /**
     * Test numeric value parsing
     */
    @Test
    public void testNumericValueParsing() throws Exception {
        // Create test Excel template
        Path templatePath = createTestExcelTemplate("numeric-test.xlsx");
        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Setup test data with numeric values
        Map<String, Object> data = new HashMap<>();
        data.put("age", "30");
        data.put("salary", "50000.50");

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("A1", "$.age");
        fieldMappings.put("B1", "$.salary");

        PageSection section = PageSection.builder()
                .sectionId("numeric_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("numeric-test.xlsx")
                .mappingType(MappingType.JSONPATH)
                .fieldMappings(fieldMappings)
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook workbook = (Workbook) context.getMetadata("excelWorkbook");
        Sheet sheet = workbook.getSheetAt(0);
        
        // Verify numeric values
        assertEquals(30.0, sheet.getRow(0).getCell(0).getNumericCellValue());
        assertEquals(50000.50, sheet.getRow(0).getCell(1).getNumericCellValue());
    }

    /**
     * Test sheet-qualified cell references
     */
    @Test
    public void testSheetQualifiedReferences() throws Exception {
        // Create multi-sheet Excel workbook
        Path templatePath = createMultiSheetTemplate("multi-sheet.xlsx");
        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        Map<String, Object> data = new HashMap<>();
        data.put("sheetOneValue", "Sheet1Data");
        data.put("sheetTwoValue", "Sheet2Data");

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("Sheet1!A1", "$.sheetOneValue");
        fieldMappings.put("Sheet2!A1", "$.sheetTwoValue");

        PageSection section = PageSection.builder()
                .sectionId("sheet_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("multi-sheet.xlsx")
                .mappingType(MappingType.JSONPATH)
                .fieldMappings(fieldMappings)
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook workbook = (Workbook) context.getMetadata("excelWorkbook");
        
        // Verify values in different sheets
        assertEquals("Sheet1Data", workbook.getSheet("Sheet1").getRow(0).getCell(0).getStringCellValue());
        assertEquals("Sheet2Data", workbook.getSheet("Sheet2").getRow(0).getCell(0).getStringCellValue());
    }

    /**
     * Test deeply nested JSONPATH expressions
     */
    @Test
    public void testNestedJsonPathMapping() throws Exception {
        Path templatePath = createTestExcelTemplate("nested-test.xlsx");
        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Setup nested data structure
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 Main St");
        address.put("city", "Springfield");
        address.put("state", "IL");  // Use non-numeric field instead
        data.put("address", address);

        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("A1", "$.address.street");
        fieldMappings.put("B1", "$.address.city");
        fieldMappings.put("C1", "$.address.state");

        PageSection section = PageSection.builder()
                .sectionId("nested_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("nested-test.xlsx")
                .mappingType(MappingType.JSONPATH)
                .fieldMappings(fieldMappings)
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook workbook = (Workbook) context.getMetadata("excelWorkbook");
        Sheet sheet = workbook.getSheetAt(0);
        
        assertEquals("123 Main St", sheet.getRow(0).getCell(0).getStringCellValue());
        assertEquals("Springfield", sheet.getRow(0).getCell(1).getStringCellValue());
        assertEquals("IL", sheet.getRow(0).getCell(2).getStringCellValue());
    }

    @Test
    public void testRendererSupportsExcelType() {
        assertTrue(renderer.supports(com.example.demo.docgen.model.SectionType.EXCEL));
        assertFalse(renderer.supports(com.example.demo.docgen.model.SectionType.ACROFORM));
        assertFalse(renderer.supports(com.example.demo.docgen.model.SectionType.FREEMARKER));
    }

    @Test
    public void testTablePopulationFromRepeatingGroup() throws Exception {
        // Create a template with some existing rows
        Path templatePath = createTestExcelTemplate("table-template.xlsx");
        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Data: list of objects
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Alice");
        item1.put("age", 30);
        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Bob");
        item2.put("age", 40);
        List<Object> items = Arrays.asList(item1, item2);

        Map<String, Object> data = new HashMap<>();
        data.put("employees", items);

        // Setup repeating group config: start at A2, columns A=name, B=age
        com.example.demo.docgen.model.RepeatingGroupConfig repeating = com.example.demo.docgen.model.RepeatingGroupConfig.builder()
                .startCell("A2")
                .insertRows(false)
                .overwrite(true)
                .fields(new HashMap<String, String>() {{
                    put("A", "name");
                    put("B", "age");
                }})
                .build();

        com.example.demo.docgen.model.FieldMappingGroup group = com.example.demo.docgen.model.FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.employees")
                .repeatingGroup(repeating)
                .build();

        PageSection section = PageSection.builder()
                .sectionId("table_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("table-template.xlsx")
                .fieldMappingGroups(Arrays.asList(group))
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook workbook = (Workbook) context.getMetadata("excelWorkbook");
        Sheet sheet = workbook.getSheetAt(0);

        // Row A2 -> row index 1
        assertEquals("Alice", sheet.getRow(1).getCell(0).getStringCellValue());
        assertEquals(30.0, sheet.getRow(1).getCell(1).getNumericCellValue());

        // Row A3 -> row index 2
        assertEquals("Bob", sheet.getRow(2).getCell(0).getStringCellValue());
        assertEquals(40.0, sheet.getRow(2).getCell(1).getNumericCellValue());
    }

    // Helper methods
    
    private Path createTestExcelTemplate(String filename) throws IOException {
        Path filePath = tempDir.resolve(filename);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0); // Create empty row
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }
        return filePath;
    }

    private Path createMultiSheetTemplate(String filename) throws IOException {
        Path filePath = tempDir.resolve(filename);
        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sheet1").createRow(0);
            workbook.createSheet("Sheet2").createRow(0);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }
        return filePath;
    }

    private byte[] readFileAsBytes(Path filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }
}
