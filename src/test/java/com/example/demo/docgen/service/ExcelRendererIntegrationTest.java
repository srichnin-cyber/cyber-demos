package com.example.demo.docgen.service;

import com.example.demo.docgen.mapper.MappingType;
import com.example.demo.docgen.model.DocumentGenerationRequest;
import com.example.demo.docgen.model.DocumentTemplate;
import com.example.demo.docgen.model.PageSection;
import com.example.demo.docgen.model.FieldMappingGroup;
import com.example.demo.docgen.model.RepeatingGroupConfig;
import com.example.demo.docgen.util.PlanComparisonTransformer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
public class ExcelRendererIntegrationTest {

    @Autowired
    private DocumentComposer composer;

    /**
     * A mockable loader so we can supply our own workbook bytes without
     * touching the classpath.  SpringBoot will replace the real bean with a
     * Mockito mock because of this annotation.
     */
    @MockBean
    private TemplateLoader templateLoader;

    /**************************************************************************
     * helpers
     **************************************************************************/

    private byte[] createTestExcelTemplate(String filename) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0);
            // add formula cell and some benefit names so later tests can target them
            Row headerRow = sheet.createRow(1);
            Cell formulaCell = headerRow.createCell(2);
            formulaCell.setCellFormula("SUM(1,1)");

            Row r1 = sheet.createRow(2);
            r1.createCell(0).setCellValue("Doctor Visits");
            Row r2 = sheet.createRow(3);
            r2.createCell(0).setCellValue("Prescriptions");
            Row r3 = sheet.createRow(4);
            r3.createCell(0).setCellValue("Emergency");

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            }
        }
    }

    private byte[] createTableTemplate(String filename, boolean withPrepopulation) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            // header row at index 3
            Row header = sheet.createRow(3);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Salary");
            header.createCell(2).setCellValue("Department");

            if (withPrepopulation) {
                Row pre = sheet.createRow(4);
                pre.createCell(0).setCellValue("PreExisting_Employee");
                pre.createCell(1).setCellValue(50000);
                // leave column C blank
            }

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                workbook.write(baos);
                return baos.toByteArray();
            }
        }
    }

    /**************************************************************************
     * tests
     **************************************************************************/

    @Test
    public void generateValuesOnlyWorkbook() throws Exception {
        // sample data load + transform (same as unit test)
        Map<String, Object> original;
        try (var is = getClass().getResourceAsStream("/plan-comparison-sample-data.json")) {
            assertNotNull(is, "sample data resource missing");
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(is, Map.class);
            original = map;
        }
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> plans = (List<Map<String,Object>>) original.get("plans");
        Map<String, Object> enriched = PlanComparisonTransformer.injectComparisonMatrixValuesOnly(original, plans);

        // assemble template definition programmatically
        PageSection section = PageSection.builder()
                .sectionId("values_only_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("comparison-template.xlsx")
                .mappingType(MappingType.JSONPATH)
                .fieldMappings(Map.of("Sheet1!B2:D4", "$.comparisonMatrixValues"))
                .build();

        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("valuesOnlyTemplate")
                .config(Map.of("columnSpacing", 1, "valuesOnly", true))
                .sections(Collections.singletonList(section))
                .build();

        byte[] workbookBytes = createTestExcelTemplate("comparison-template.xlsx");
        when(templateLoader.loadTemplate(anyString(), anyString(), anyMap())).thenReturn(template);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(workbookBytes);

        DocumentGenerationRequest req = DocumentGenerationRequest.builder()
                .namespace("common-templates")
                .templateId("valuesOnlyTemplate")
                .data(enriched)
                .build();

        byte[] result = composer.generateExcel(req);
        assertNotNull(result);
        assertTrue(result.length > 0);

        Path out = Paths.get("docs/sample-values-only.xlsx");
        Files.createDirectories(out.getParent());
        Files.write(out, result);
    }

    @Test
    public void generateOverwriteAndPreserveWorkbook() throws Exception {
        // two scenarios: overwrite=true and overwrite=false
        byte[] baseBytes = createTableTemplate("employee-table.xlsx", true);

        RepeatingGroupConfig repeatingOverwrite = RepeatingGroupConfig.builder()
                .startCell("Sheet1!A5")
                .insertRows(false)
                .overwrite(true)
                .fields(Map.of("A", "name", "B", "salary", "C", "department"))
                .build();

        FieldMappingGroup groupOverwrite = FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.employees")
                .repeatingGroup(repeatingOverwrite)
                .build();

        PageSection sectionOverwrite = PageSection.builder()
                .sectionId("employee_table_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("employee-table.xlsx")
                .fieldMappingGroups(Collections.singletonList(groupOverwrite))
                .build();

        DocumentTemplate templateOverwrite = DocumentTemplate.builder()
                .templateId("employeeTableOverwrite")
                .sections(Collections.singletonList(sectionOverwrite))
                .build();

        // sample data (three employees)
        Map<String, Object> emp1 = new HashMap<>(); emp1.put("name","Alice"); emp1.put("salary",95000); emp1.put("department","Engineering");
        Map<String, Object> emp2 = new HashMap<>(); emp2.put("name","Bob"); emp2.put("salary",85000); emp2.put("department","Engineering");
        Map<String, Object> emp3 = new HashMap<>(); emp3.put("name","Carol"); emp3.put("salary",75000); emp3.put("department","Sales");
        Map<String, Object> data = new HashMap<>();
        data.put("employees", List.of(emp1, emp2, emp3));

        when(templateLoader.loadTemplate(anyString(), anyString(), anyMap())).thenReturn(templateOverwrite);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(baseBytes);

        DocumentGenerationRequest req1 = DocumentGenerationRequest.builder()
                .namespace("common-templates")
                .templateId("employeeTableOverwrite")
                .data(data)
                .build();

        byte[] res1 = composer.generateExcel(req1);
        assertTrue(res1.length > 0);
        Path out1 = Paths.get("docs/sample-overwrite.xlsx");
        Files.createDirectories(out1.getParent());
        Files.write(out1, res1);

        // now repeat with overwrite=false to exercise preservation behavior
        RepeatingGroupConfig repeatingPreserve = RepeatingGroupConfig.builder()
                .startCell("Sheet1!A5")
                .insertRows(false)
                .overwrite(false)
                .fields(Map.of("A", "name", "B", "salary", "C", "department"))
                .build();
        FieldMappingGroup groupPreserve = FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.employees")
                .repeatingGroup(repeatingPreserve)
                .build();
        PageSection sectionPreserve = PageSection.builder()
                .sectionId("employee_preserve_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("employee-table.xlsx")
                .fieldMappingGroups(Collections.singletonList(groupPreserve))
                .build();
        DocumentTemplate templatePreserve = DocumentTemplate.builder()
                .templateId("employeeTablePreserve")
                .sections(Collections.singletonList(sectionPreserve))
                .build();

        when(templateLoader.loadTemplate(anyString(), anyString(), anyMap())).thenReturn(templatePreserve);
        // reuse same base bytes

        DocumentGenerationRequest req2 = DocumentGenerationRequest.builder()
                .namespace("common-templates")
                .templateId("employeeTablePreserve")
                .data(data)
                .build();
        byte[] res2 = composer.generateExcel(req2);
        assertTrue(res2.length > 0);
        Path out2 = Paths.get("docs/sample-preserve.xlsx");
        Files.createDirectories(out2.getParent());
        Files.write(out2, res2);
    }

    @Test
    public void generateNumberedFieldModeWorkbook() throws Exception {
        // simple blank template is fine; the generated workbook isn't really used
        byte[] tplBytes = createTestExcelTemplate("form-template.xlsx");

        RepeatingGroupConfig repeating = RepeatingGroupConfig.builder()
                .prefix("child")
                .indexSeparator("_")
                .indexPosition(RepeatingGroupConfig.IndexPosition.BEFORE_FIELD)
                .startIndex(1)
                .maxItems(5)
                .fields(Map.of("firstName", "$.firstName", "age", "$.age"))
                .build();
        FieldMappingGroup group = FieldMappingGroup.builder()
                .mappingType(MappingType.JSONPATH)
                .basePath("$.children")
                .repeatingGroup(repeating)
                .build();
        PageSection section = PageSection.builder()
                .sectionId("children_form_test")
                .type(com.example.demo.docgen.model.SectionType.EXCEL)
                .templatePath("form-template.xlsx")
                .fieldMappingGroups(Collections.singletonList(group))
                .build();
        DocumentTemplate template = DocumentTemplate.builder()
                .templateId("childrenForm")
                .sections(Collections.singletonList(section))
                .build();

        Map<String, Object> child1 = new HashMap<>(); child1.put("firstName","Emma"); child1.put("age",8);
        Map<String, Object> child2 = new HashMap<>(); child2.put("firstName","Liam"); child2.put("age",6);
        Map<String, Object> data = new HashMap<>(); data.put("children", List.of(child1, child2));

        when(templateLoader.loadTemplate(anyString(), anyString(), anyMap())).thenReturn(template);
        when(templateLoader.getResourceBytes(anyString())).thenReturn(tplBytes);

        DocumentGenerationRequest req = DocumentGenerationRequest.builder()
                .namespace("common-templates")
                .templateId("childrenForm")
                .data(data)
                .build();
        byte[] res = composer.generateExcel(req);
        assertTrue(res.length > 0);
        Path out = Paths.get("docs/sample-numbered-fields.xlsx");
        Files.createDirectories(out.getParent());
        Files.write(out, res);
    }
}
