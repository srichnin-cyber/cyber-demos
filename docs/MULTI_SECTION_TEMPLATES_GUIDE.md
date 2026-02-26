templateId: multi-section-report
description: Report with multiple Excel sections demonstrating different configurations

# Multiple sections allow you to:
# 1. Apply different mappings to the same template/sheet
# 2. Populate different sheets within the same workbook
# 3. Apply sequential transformations to the same workbook
#
# IMPORTANT: All sections update the SAME in-memory workbook stored in RenderContext.
# If sections reference different templatePaths:
#   - Each loads its own template file
#   - The LAST section's workbook becomes the final output
#   - Unless they target the same base template (recommended for multi-sheet scenarios)

config:
  columnSpacing: 1

sections:
  # ============================================================================
  # SECTION 1: Summary Page
  # ============================================================================
  # This section loads the template and fills basic summary information
  - sectionId: summary_section
    type: EXCEL
    templatePath: report-template.xlsx
    mappingType: JSONPATH
    order: 1  # Process first
    fieldMappings:
      "Summary!A1": "$.companyName"
      "Summary!A2": "$.reportYear"
      "Summary!B5": "$.totalRevenue"
      "Summary!B6": "$.totalExpenses"
      "Summary!B7": "$.netIncome"

  # ============================================================================
  # SECTION 2: Employee Data Table
  # ============================================================================
  # Same template, different sheet. Uses repeating group for table population.
  # Since templatePath is the same, it operates on the SAME workbook instance.
  - sectionId: employee_table
    type: EXCEL
    templatePath: report-template.xlsx  # SAME TEMPLATE
    order: 2  # Process second
    fieldMappingGroups:
      - mappingType: JSONPATH
        basePath: "$.employees"
        repeatingGroup:
          startCell: "Employees!A3"  # Different sheet, different starting cell
          insertRows: false
          overwrite: true
          maxItems: 1000
          fields:
            "A": "firstName"
            "B": "lastName"
            "C": "department"
            "D": "salary"

  # ============================================================================
  # SECTION 3: Detailed Metrics (with conditional rendering)
  # ============================================================================
  # Only rendered if detailed data is available
  - sectionId: detailed_metrics
    type: EXCEL
    templatePath: report-template.xlsx  # SAME TEMPLATE
    order: 3  # Process third
    condition: "$.includeDetailedMetrics"  # Conditional rendering
    fieldMappings:
      "Metrics!A1": "$.metricsTitle"
      "Metrics!A2:E50": "$.detailedMetricsMatrix"

  # ============================================================================
  # SECTION 4: Plan Comparison (with values-only mapping)
  # ============================================================================
  # Fill plan comparison avoiding template formulas
  - sectionId: plan_comparison
    type: EXCEL
    templatePath: report-template.xlsx  # SAME TEMPLATE
    order: 4  # Process fourth
    mappingType: JSONPATH
    fieldMappings:
      "PlansComparison!B2:D4": "$.comparisonMatrixValues"  # Values-only (preserves formulas)

---

## Processing Flow & Output

### Input Data:
```json
{
  "companyName": "Acme Corp",
  "reportYear": 2025,
  "totalRevenue": 1500000,
  "totalExpenses": 1000000,
  "netIncome": 500000,
  "includeDetailedMetrics": true,
  "metricsTitle": "Q4 Detailed Analysis",
  "detailedMetricsMatrix": [[...], [...], ...],
  "comparisonMatrixValues": [[...], [...], ...],
  "employees": [
    { "firstName": "Alice", "lastName": "Johnson", "department": "Engineering", "salary": 95000 },
    { "firstName": "Bob", "lastName": "Smith", "department": "Sales", "salary": 75000 },
    { "firstName": "Carol", "lastName": "Davis", "department": "HR", "salary": 65000 }
  ]
}
```

### Processing Order:
1. **Section 1** (order: 1)
   - Loads `report-template.xlsx`
   - Fills Summary sheet: A1=Acme Corp, A2=2025, B5=1500000, etc.
   - Stores workbook in context

2. **Section 2** (order: 2)
   - Reuses same template (same templatePath)
   - Continues with existing workbook from context
   - Populates Employees sheet (A3:D5) with 3 employee rows
   - Updates workbook in context

3. **Section 3** (order: 3)
   - Checks condition: `$.includeDetailedMetrics` = true → RENDER
   - Continues with existing workbook
   - Populates Metrics sheet: title and matrix data
   - Updates workbook in context

4. **Section 4** (order: 4)
   - Continues with existing workbook
   - Fills PlansComparison sheet (B2:D4) with values-only matrix
   - Preserves any formulas in the range
   - Final workbook is ready for output

### Final Output:
```
report-template.xlsx (single workbook with 4 sheets)
├── Summary
│   └── Company: Acme Corp, Year: 2025, Revenue: 1500000, Expenses: 1000000, Net Income: 500000
├── Employees
│   └── 3 rows of employee data (Alice, Bob, Carol)
├── Metrics
│   └── Title: Q4 Detailed Analysis + detailed matrix data
└── PlansComparison
    └── Plan comparison values with preserved formulas
```

---

## Scenario Variations

### Scenario A: Same Template, Different Sheets (RECOMMENDED)

```yaml
sections:
  - sectionId: page1
    templatePath: multi-sheet-template.xlsx
    fieldMappings:
      "Sheet1!A1": "$.data1"
  - sectionId: page2
    templatePath: multi-sheet-template.xlsx  # SAME FILE
    fieldMappings:
      "Sheet2!A1": "$.data2"
  - sectionId: page3
    templatePath: multi-sheet-template.xlsx  # SAME FILE
    fieldMappings:
      "Sheet3!A1": "$.data3"
```

**Output:** Single workbook with 3 populated sheets, all filled in sequence.

**Advantage:** Clean, predictable; all sections operate on same workbook.

---

### Scenario B: Different Templates (Use with Caution)

```yaml
sections:
  - sectionId: cover_page
    templatePath: cover-template.xlsx
    fieldMappings:
      "A1": "$.reportTitle"
  - sectionId: detail_page
    templatePath: detail-template.xlsx  # DIFFERENT FILE
    fieldMappings:
      "A1": "$.detailData"
```

**Output:** Single workbook from `detail-template.xlsx` only.

**Issue:** First template is loaded, filled, then OVERWRITTEN by second template.

**Only Use If:**
- Intentionally want only the last template's output
- Using a base template that gets progressively filled

---

### Scenario C: Conditional Sections

```yaml
sections:
  - sectionId: summary
    templatePath: report.xlsx
    order: 1
    fieldMappings:
      "A1": "$.title"
  
  - sectionId: advanced_metrics
    templatePath: report.xlsx
    order: 2
    condition: "$.userRole == 'admin'"  # Only render for admins
    fieldMappings:
      "AdminMetrics!A1:C50": "$.confidentialData"
  
  - sectionId: public_summary
    templatePath: report.xlsx
    order: 3
    condition: "$.userRole != 'admin'"  # Render for non-admins
    fieldMappings:
      "PublicView!A1:C50": "$.publicData"
```

**Output:** 
- Admins see: Summary + AdminMetrics sheets
- Non-admins see: Summary + PublicView sheets

---

### Scenario D: Sequential Enrichment

```yaml
sections:
  - sectionId: base_data
    templatePath: workbook.xlsx
    order: 1
    fieldMappings:
      "A1:D100": "$.rawData"
  
  - sectionId: calculated_totals
    templatePath: workbook.xlsx
    order: 2
    fieldMappings:
      "A101": "$.totalRow"  # Adds totals AFTER base data
  
  - sectionId: summary_stats
    templatePath: workbook.xlsx
    order: 3
    fieldMappings:
      "Stats!A1": "$.averageValue"
      "Stats!B1": "$.minValue"
      "Stats!C1": "$.maxValue"
```

**Output:** Single workbook with base data, totals, and calculated statistics all applied.

---

## Key Points

### 1. **Order Matters**
Sections are processed in `order` sequence. Lower order = earlier execution.

```yaml
order: 1  # Executed first
order: 2  # Executed second
order: 3  # Executed third
```

### 2. **Shared Workbook State**
All sections share the same in-memory workbook via RenderContext:

```
Section 1: Load template → Fill cells → Store in context
     ↓
Section 2: Load (or reuse) template → Fill MORE cells → Update context
     ↓
Section 3: Continue with existing workbook → Fill additional cells
     ↓
Final output: Single merged workbook
```

### 3. **Template Path Logic**

| Scenario | Result |
|----------|--------|
| All sections use SAME templatePath | Workbook is loaded once, multiple sections fill different cells/sheets |
| Different templatePaths | Each section loads its template; LAST one becomes final output |
| Repeating group with multiple field mappings | All mappings applied to same workbook |

### 4. **Sheet Targeting**

Use sheet-qualified references to target specific sheets:

```yaml
fieldMappings:
  "Sheet1!A1": "$.data1"     # Fills Sheet1
  "Sheet2!A1": "$.data2"     # Fills Sheet2 in SAME workbook
  "Sheet3!B5:D10": "$.matrix" # Fills Sheet3 range
```

---

## Best Practices for Multiple Sections

### ✅ DO:
1. Use the SAME base template for all sections
2. Target different sheets for different data types
3. Set explicit `order` values for clarity
4. Use `condition` to control section rendering
5. Use descriptive `sectionId` names

### ❌ DON'T:
1. Use different baseline templates (confusing output)
2. Forget to set `order` for complex workflows
3. Mix repeating groups and simple mappings on same cells
4. Assume multiple different templatePaths will "merge" (they won't)

---

## Java Example

```java
DocumentGenerationRequest request = DocumentGenerationRequest.builder()
    .namespace("common-templates")
    .templateId("multi-section-report")
    .data(Map.of(
        "companyName", "Acme Corp",
        "reportYear", 2025,
        "totalRevenue", 1500000L,
        "totalExpenses", 1000000L,
        "netIncome", 500000L,
        "includeDetailedMetrics", true,
        "employees", List.of(
            Map.of("firstName", "Alice", "lastName", "Johnson", "department", "Engineering", "salary", 95000),
            Map.of("firstName", "Bob", "lastName", "Smith", "department", "Sales", "salary", 75000)
        ),
        "detailedMetricsMatrix", List.of(...),  // 2D list
        "comparisonMatrixValues", List.of(...)  // 2D list
    ))
    .build();

// All 4 sections are processed in order and merged into single workbook
byte[] xlsx = documentComposer.generateExcel(request);

// Write to file or return via HTTP
Files.write(Paths.get("report.xlsx"), xlsx);
```

---

## Testing Multiple Sections

```java
@SpringBootTest
public class MultiSectionExcelTest {
    @Autowired
    private DocumentComposer composer;

    @Test
    public void testMultipleSectionsPopulatesSingleWorkbook() throws Exception {
        Map<String, Object> data = createMultiSectionData();
        
        DocumentGenerationRequest req = DocumentGenerationRequest.builder()
            .namespace("common-templates")
            .templateId("multi-section-report")
            .data(data)
            .build();

        byte[] xlsx = composer.generateExcel(req);
        
        // Verify output
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            // Section 1: Summary sheet
            Sheet summary = workbook.getSheet("Summary");
            assertEquals("Acme Corp", summary.getRow(0).getCell(0).getStringCellValue());
            
            // Section 2: Employees sheet
            Sheet employees = workbook.getSheet("Employees");
            assertEquals("Alice", employees.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Bob", employees.getRow(3).getCell(0).getStringCellValue());
            
            // Section 3: Metrics sheet
            Sheet metrics = workbook.getSheet("Metrics");
            assertEquals("Q4 Detailed Analysis", metrics.getRow(1).getCell(0).getStringCellValue());
            
            // Section 4: PlansComparison sheet
            Sheet plans = workbook.getSheet("PlansComparison");
            Cell formulaCell = plans.getRow(1).getCell(1);
            assertEquals(CellType.FORMULA, formulaCell.getCellType());  // Formula preserved
        }
    }
}
```

---

## Summary

Multiple sections with different `sectionId`s are **fully supported** and provide powerful composition capabilities:

- **Same templatePath + different sheets** → Recommended approach for multi-sheet workbooks
- **Different data mapping types** → Apply JSONPATH, numeric, repeating groups to same workbook
- **Conditional rendering** → Render sections based on data conditions
- **Sequential filling** → Build up workbook across multiple sections
- **Final output** → Single merged workbook combining all section outputs
