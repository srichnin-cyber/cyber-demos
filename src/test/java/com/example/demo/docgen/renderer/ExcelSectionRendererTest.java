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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    @Test
    public void testValuesOnlyRangePreservesFormulas() throws Exception {
        // Read YAML to find the configured templatePath and create a matching temp workbook
        String yamlFile = "src/main/resources/common-templates/templates/plan-comparison-two-row.yaml";
        String yamlContent = new String(Files.readAllBytes(Paths.get(yamlFile)), StandardCharsets.UTF_8);
        Pattern p = Pattern.compile("templatePath:\\s*(\\S+)");
        Matcher m = p.matcher(yamlContent);
        String templateFileName = m.find() ? m.group(1) : "comparison-template.xlsx";

        Path templatePath = createTestExcelTemplate(templateFileName);
        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Load sample data from test resources
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try (InputStream is = getClass().getResourceAsStream("/plan-comparison-sample-data.json")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> original = mapper.readValue(is, Map.class);

            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> plans = (java.util.List<java.util.Map<String, Object>>) original.get("plans");

            // Inject values-only matrix into data
            Map<String, Object> enriched = com.example.demo.docgen.util.PlanComparisonTransformer.injectComparisonMatrixValuesOnly(original, plans);

            // Create a PageSection mapping the values-only matrix into B2:E5 (4x4 grid)
            Map<String, String> fieldMappings = new HashMap<>();
            fieldMappings.put("Sheet1!B2:E5", "$.comparisonMatrixValues");

            PageSection section = PageSection.builder()
                    .sectionId("values_only_test")
                    .type(com.example.demo.docgen.model.SectionType.EXCEL)
                    .templatePath("plan-comparison-template.xlsx")
                    .mappingType(com.example.demo.docgen.mapper.MappingType.JSONPATH)
                    .fieldMappings(fieldMappings)
                    .build();

            RenderContext context = new RenderContext(null, enriched);

            // Execute render
            renderer.render(section, context);

            Workbook out = (Workbook) context.getMetadata("excelWorkbook");
            assertNotNull(out);
            Sheet outSheet = out.getSheet("Sheet1");

            // Verify that the formula originally in C2 (row idx 1, col idx 2) was preserved
            Cell c2 = outSheet.getRow(1).getCell(2);
            assertNotNull(c2);
            assertEquals(org.apache.poi.ss.usermodel.CellType.FORMULA, c2.getCellType(), "Formula cell should be preserved");
            assertEquals("SUM(1,1)", c2.getCellFormula());

            // Verify some written values from the matrix (e.g., Basic plan value for Doctor Visits should be in C3)
            Cell c3 = outSheet.getRow(2).getCell(2);
            assertNotNull(c3);
            assertEquals("$20 copay", c3.getStringCellValue());

            // Verify Premium plan header exists at E2 (col idx 4)
            Cell e2 = outSheet.getRow(1).getCell(4);
            assertNotNull(e2);
            assertEquals("Premium", e2.getStringCellValue());
        }
    }


    /**
     * When a merged region spans multiple cells, only the top-left cell should
     * be updated by a range fill.  This test creates a merge and then
     * supplies a 2‑element list to the range A1:A2.  The renderer must write
     * the first value into A1 and skip A2 (it belongs to the same merged
     * region).
     */
    @Test
    public void testSkipNonLeadingMergedCells() throws Exception {
        // create template with merged A1:A2
        Path templatePath = tempDir.resolve("merged-test.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 1, 0, 0));
            try (FileOutputStream fos = new FileOutputStream(templatePath.toFile())) {
                workbook.write(fos);
            }
        }
        byte[] bytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(bytes);

        // create data for two rows
        List<List<Object>> matrix = Arrays.asList(
                Collections.singletonList((Object)"FIRST"),
                Collections.singletonList((Object)"SECOND")
        );
        Map<String, Object> data = new HashMap<>();
        data.put("matrix", matrix);

        PageSection section = PageSection.builder()
                .sectionId("merged_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("merged-test.xlsx")
                .mappingType(com.example.demo.docgen.mapper.MappingType.JSONPATH)
                .fieldMappings(Collections.singletonMap("Sheet1!A1:A2", "$.matrix"))
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook out = (Workbook) context.getMetadata("excelWorkbook");
        Sheet outSheet = out.getSheet("Sheet1");
        // A1 should have FIRST
        assertEquals("FIRST", outSheet.getRow(0).getCell(0).getStringCellValue());
        // A2 is part of same merged region so should not be written (remains blank)
        Cell a2 = outSheet.getRow(1).getCell(0);
        assertTrue(a2 == null || a2.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK,
                "Merged second cell should not be written");
    }

    // Helper methods
    
    private Path createTestExcelTemplate(String filename) throws IOException {
        Path filePath = tempDir.resolve(filename);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0); // Create empty row

            // Add a formula in C2 and sample benefit names in column A to support values-only tests
            Row headerRow = sheet.createRow(1);
            Cell formulaCell = headerRow.createCell(2);
            formulaCell.setCellFormula("SUM(1,1)");

            Row r1 = sheet.createRow(2);
            r1.createCell(0).setCellValue("Doctor Visits");
            Row r2 = sheet.createRow(3);
            r2.createCell(0).setCellValue("Prescriptions");
            Row r3 = sheet.createRow(4);
            r3.createCell(0).setCellValue("Emergency");
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

    /**
     * TABLE MODE: Repeating group with startCell (recommended for Excel tables)
     * 
     * Demonstrates:
     * - Writing employee list to consecutive rows starting at A5
     * - Column mapping: A=name, B=salary, C=department
     * - No row insertion (insertRows: false)
     * - Overwrite enabled (default)
     */
    @Test
    public void testRepeatingGroupTableModeExample() throws Exception {
        // Create template with headers
        Path templatePath = createTestExcelTemplate("employee-table.xlsx");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(readFileAsBytes(templatePath)))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.createRow(3); // row index 3 = row 4(display)
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Salary");
            headerRow.createCell(2).setCellValue("Department");
            try (FileOutputStream fos = new FileOutputStream(templatePath.toFile())) {
                workbook.write(fos);
            }
        }

        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Sample data: employee list
        Map<String, Object> emp1 = new HashMap<>();
        emp1.put("name", "Alice Johnson");
        emp1.put("salary", 95000);
        emp1.put("department", "Engineering");

        Map<String, Object> emp2 = new HashMap<>();
        emp2.put("name", "Bob Smith");
        emp2.put("salary", 85000);
        emp2.put("department", "Engineering");

        Map<String, Object> emp3 = new HashMap<>();
        emp3.put("name", "Carol Davis");
        emp3.put("salary", 75000);
        emp3.put("department", "Sales");

        Map<String, Object> data = new HashMap<>();
        data.put("employees", Arrays.asList(emp1, emp2, emp3));

        // Configure repeating group for table mode
        com.example.demo.docgen.model.RepeatingGroupConfig repeating = com.example.demo.docgen.model.RepeatingGroupConfig.builder()
                .startCell("Sheet1!A5")
                .insertRows(false)
                .overwrite(true)
                .maxItems(10)
                .fields(new HashMap<String, String>() {{
                    put("A", "name");
                    put("B", "salary");
                    put("C", "department");
                }})
                .build();

        com.example.demo.docgen.model.FieldMappingGroup group = com.example.demo.docgen.model.FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.employees")
                .repeatingGroup(repeating)
                .build();

        PageSection section = PageSection.builder()
                .sectionId("employee_table_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("employee-table.xlsx")
                .fieldMappingGroups(Arrays.asList(group))
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook out = (Workbook) context.getMetadata("excelWorkbook");
        Sheet outSheet = out.getSheet("Sheet1");

        // Verify employees written to consecutive rows starting at A5 (index 4)
        assertEquals("Alice Johnson", outSheet.getRow(4).getCell(0).getStringCellValue());
        assertEquals(95000.0, outSheet.getRow(4).getCell(1).getNumericCellValue());
        assertEquals("Engineering", outSheet.getRow(4).getCell(2).getStringCellValue());

        assertEquals("Bob Smith", outSheet.getRow(5).getCell(0).getStringCellValue());
        assertEquals(85000.0, outSheet.getRow(5).getCell(1).getNumericCellValue());

        assertEquals("Carol Davis", outSheet.getRow(6).getCell(0).getStringCellValue());
        assertEquals(75000.0, outSheet.getRow(6).getCell(1).getNumericCellValue());
        assertEquals("Sales", outSheet.getRow(6).getCell(2).getStringCellValue());
    }

    /**
     * TABLE MODE with OVERWRITE=FALSE: Preserves existing cell data
     * 
     * Demonstrates:
     * - overwrite: false skips non-blank cells
     * - Existing template values are preserved
     * - Empty cells are filled with new data
     */
    @Test
    public void testRepeatingGroupTableModePreservesExisting() throws Exception {
        // Create template with pre-existing data
        Path templatePath = tempDir.resolve("employee-existing.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            
            // Header row at index 3 (display row 4)
            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Salary");
            headerRow.createCell(2).setCellValue("Department");
            
            // Pre-populate first employee row at index 4 (display row 5, matches startCell A5)
            Row prefilledRow = sheet.createRow(4);
            prefilledRow.createCell(0).setCellValue("PreExisting_Employee");
            prefilledRow.createCell(1).setCellValue(50000);
            // Note: Department (column C) is intentionally left empty
            
            try (FileOutputStream fos = new FileOutputStream(templatePath.toFile())) {
                workbook.write(fos);
            }
        }

        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Only one employee in data (will be placed in first row)
        Map<String, Object> emp1 = new HashMap<>();
        emp1.put("name", "New_Employee");
        emp1.put("salary", 95000);
        emp1.put("department", "Engineering");

        Map<String, Object> data = new HashMap<>();
        data.put("employees", Arrays.asList(emp1));

        // Configure with overwrite=false to preserve existing values
        com.example.demo.docgen.model.RepeatingGroupConfig repeating = com.example.demo.docgen.model.RepeatingGroupConfig.builder()
                .startCell("Sheet1!A5")
                .insertRows(false)
                .overwrite(false)  // Preserve existing values
                .fields(new HashMap<String, String>() {{
                    put("A", "name");
                    put("B", "salary");
                    put("C", "department");
                }})
                .build();

        com.example.demo.docgen.model.FieldMappingGroup group = com.example.demo.docgen.model.FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.employees")
                .repeatingGroup(repeating)
                .build();

        PageSection section = PageSection.builder()
                .sectionId("preserve_existing_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("employee-existing.xlsx")
                .fieldMappingGroups(Arrays.asList(group))
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        Workbook out = (Workbook) context.getMetadata("excelWorkbook");
        Sheet outSheet = out.getSheet("Sheet1");

        // Verify existing values were preserved (not overwritten) at row index 4
        assertEquals("PreExisting_Employee", outSheet.getRow(4).getCell(0).getStringCellValue(), 
            "Existing Name preserved with overwrite=false");
        assertEquals(50000.0, outSheet.getRow(4).getCell(1).getNumericCellValue(), 
            "Existing Salary preserved with overwrite=false");
        
        // Empty Department cell should be filled
        Cell departmentCell = outSheet.getRow(4).getCell(2);
        assertNotNull(departmentCell, "Department cell should exist");
        assertEquals("Engineering", departmentCell.getStringCellValue(), 
            "Empty Department cell filled");
    }

    /**
     * NUMBERED FIELD MODE: Generates indexed field names (PDF-style)
     * 
     * Demonstrates:
     * - Without startCell, numbered field names are generated
     * - Naming pattern: prefix + index + separator + fieldKey
     * - Useful for PDF AcroForm repeating fields
     * 
     * Note: This primarily applies to PDF forms, not Excel direct cell writes.
     */
    @Test
    public void testRepeatingGroupNumberedFieldModeExample() throws Exception {
        // Create basic template
        Path templatePath = createTestExcelTemplate("form-template.xlsx");
        byte[] templateBytes = readFileAsBytes(templatePath);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(templateBytes);

        // Sample data: children list
        Map<String, Object> child1 = new HashMap<>();
        child1.put("firstName", "Emma");
        child1.put("age", 8);

        Map<String, Object> child2 = new HashMap<>();
        child2.put("firstName", "Liam");
        child2.put("age", 6);

        Map<String, Object> data = new HashMap<>();
        data.put("children", Arrays.asList(child1, child2));

        // Configure repeating group with NUMBERED FIELD MODE (no startCell)
        // This generates field names like: child1_firstName, child1_age, child2_firstName, child2_age
        com.example.demo.docgen.model.RepeatingGroupConfig repeating = com.example.demo.docgen.model.RepeatingGroupConfig.builder()
                .prefix("child")                              // Field prefix
                .indexSeparator("_")                          // Separator
                .indexPosition(com.example.demo.docgen.model.RepeatingGroupConfig.IndexPosition.BEFORE_FIELD)
                .startIndex(1)                                // Start from 1 (child1, child2, ...)
                .maxItems(5)
                .fields(new HashMap<String, String>() {{
                    put("firstName", "$.firstName");
                    put("age", "$.age");
                }})
                .build();

        com.example.demo.docgen.model.FieldMappingGroup group = com.example.demo.docgen.model.FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.children")
                .repeatingGroup(repeating)
                .build();

        PageSection section = PageSection.builder()
                .sectionId("children_form_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("form-template.xlsx")
                .fieldMappingGroups(Arrays.asList(group))
                .build();

        RenderContext context = new RenderContext(null, data);
        renderer.render(section, context);

        // Verify rendering completes successfully
        Workbook out = (Workbook) context.getMetadata("excelWorkbook");
        assertNotNull(out, "Workbook should be in context");
        
        // In numbered field mode, field names are generated for PDF form binding.
        // Generated names would be: child1_firstName, child1_age, child2_firstName, child2_age
        // For Excel, this mode doesn't write directly but is used for PDF AcroForm fields.
    }
}
