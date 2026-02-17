# Excel Section Renderer - Comprehensive Testing Guide

## Overview

The Excel Section Renderer enables filling Excel templates with data using the same mapping strategies as PDF/AcroForm rendering. This guide covers all testing approaches.

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Application running on `http://localhost:8080`

### Run Unit Tests
```bash
mvn test -Dtest=ExcelSectionRendererTest
```

Expected Output:
```
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
```

---

## Testing Levels

### Level 1: Unit Tests (Fastest)

**File:** `src/test/java/com/example/demo/docgen/renderer/ExcelSectionRendererTest.java`

**What's Tested:**
- ✅ Basic cell filling with JSONPATH mappings
- ✅ Numeric value parsing and type detection
- ✅ Sheet-qualified cell references (e.g., `Sheet1!A1`)
- ✅ Nested JSON path expressions (e.g., `$.address.city`)
- ✅ Renderer type support verification

**Run:**
```bash
mvn test -Dtest=ExcelSectionRendererTest -v
```

**Key Test Cases:**

1. **testBasicExcelCellFilling** - Verifies simple cell population
   - Input: `{firstName: "John", lastName: "Doe", email: "john@example.com"}`
   - Mapping: `A1 -> $.firstName`, `B1 -> $.lastName`, `C1 -> $.email`
   - Verify: Workbook contains correct values

2. **testNumericValueParsing** - Ensures numeric detection
   - Input: `{age: "30", salary: "50000.50"}`
   - Verify: Values stored as numeric cells (not strings)

3. **testSheetQualifiedReferences** - Tests multi-sheet templates
   - Input: Multiple sheets with qualified references
   - Mapping: `Sheet1!A1`, `Sheet2!A1`
   - Verify: Values written to correct sheets

4. **testNestedJsonPathMapping** - Tests complex data structures
   - Input: `{address: {street: "123 Main St", city: "Springfield", state: "IL"}}`
   - Verify: Nested values correctly extracted and written

---

### Level 2: Integration Tests (Requires Running App)

**File:** `src/test/resources/templates/excel-enrollment.yaml`

**Test Data:** `src/test/resources/test-data/excel-enrollment-data.json`

#### Option A: Using curl

```bash
# Start application
mvn spring-boot:run

# In another terminal
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "excel-enrollment",
    "data": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com",
      "phoneNumber": "555-1234",
      "address": {
        "street": "123 Main St",
        "city": "Springfield",
        "state": "IL",
        "zipCode": "62701"
      },
      "policyNumber": "POL-001",
      "coverageType": "Family",
      "effectiveDate": "2026-03-01",
      "planName": "Gold"
    }
  }' -v
```

#### Option B: Using test script

```bash
chmod +x test-excel-renderer.sh
./test-excel-renderer.sh
```

#### Expected Behavior:

✅ Response contains PDF (currently per interface design)  
✅ Excel workbook stored in context metadata (visible in logs)  
✅ All requested cells populated with correct values  
✅ No exceptions thrown  

#### Verify in Logs:

Look for log messages like:
```
INFO ExcelSectionRenderer -- Rendering Excel section: personal_info with template: forms/enrollment-template.xlsx
INFO ExcelSectionRenderer -- Mapped field values for Excel: {A1=John, B1=Doe, C1=john@example.com, ...}
INFO ExcelSectionRenderer -- Excel section rendered successfully. Workbook stored in context for output.
```

---

### Level 3: Manual Testing with Different Scenarios

#### Scenario 1: Repeating Groups (Arrays)

Future enhancement - test when implemented.

#### Scenario 2: Multiple Mapping Groups

YAML example:
```yaml
sections:
  - sectionId: mixed_mapping
    type: excel
    templatePath: forms/template.xlsx
    fieldMappingGroups:
      - mappingType: JSONPATH
        fields:
          A1: "$.personal.firstName"
          B1: "$.personal.lastName"
      - mappingType: DIRECT
        fields:
          C1: "address.street"
          D1: "address.city"
```

#### Scenario 3: Error Handling

Test these error cases:

1. **Missing Template**
   ```bash
   curl -X POST http://localhost:8080/api/documents/generate \
     -H "Content-Type: application/json" \
     -d '{
       "namespace": "common-templates",
       "templateId": "nonexistent-excel",
       "data": {}
     }'
   ```
   Expected: `404 Resource not found` or similar error

2. **Invalid Cell Reference**
   - Template: `{invalid-cell: "$.data"}`
   - Expected: Logs warning, continues with next cell

3. **Missing Data Paths**
   - Mapping: `A1 -> $.missing.field`
   - Expected: Cell remains empty or contains null

---

## Testing With Different Mapping Strategies

### JSONPATH Strategy (Primary)

```yaml
fieldMappings:
  A1: "$.firstName"
  B1: "$.address.zipCode"
  C1: "$['complex']['path']"
```

**Test Data:**
```json
{
  "firstName": "John",
  "address": {
    "zipCode": "12345"
  },
  "complex": {
    "path": "value"
  }
}
```

### DIRECT Strategy

```yaml
fieldMappings:
  A1: "firstName"
  B1: "address.zipCode"
```

Same test data as JSONPATH.

### Combining Strategies

```yaml
fieldMappingGroups:
  - mappingType: JSONPATH
    fields:
      A1: "$.firstName"
  - mappingType: DIRECT
    fields:
      B1: "lastName"
```

---

## Performance Testing

### Scenario: Large Excel Template (100+ cells)

```bash
# Create large test with many cells
time mvn test -Dtest=ExcelSectionRendererTest
```

Expected: < 5 seconds per test

### Scenario: Multiple Sheets

Template with 5 sheets, 50 cells each = 250 total cells

```bash
# Should complete without memory issues
mvn test -Dtest=ExcelSectionRendererTest -Xmx2g
```

---

## Debugging

### Enable Debug Logging

In `src/test/resources/application-test.yml`:
```yaml
logging:
  level:
    com.example.demo.docgen.renderer.ExcelSectionRenderer: DEBUG
```

### Print Workbook Contents

In test, after rendering:
```java
Workbook workbook = (Workbook) context.getMetadata("excelWorkbook");
Sheet sheet = workbook.getSheetAt(0);

// Print all non-empty cells
for (Row row : sheet) {
    for (Cell cell : row) {
        System.out.println("Cell [" + row.getRowNum() + "," + cell.getColumnIndex() + "]: " + cell.getStringCellValue());
    }
}
```

---

## Future Enhancements To Test

Once implemented:

1. **Native Excel Output**
   - Test downloading Excel files directly
   - Test content-type headers

2. **Excel-to-PDF Conversion**
   - Verify PDF contains Excel data
   - Test formatting preservation

3. **Advanced Cell Formatting**
   - Number formats
   - Date formats
   - Cell styling

4. **Data Validation**
   - Excel dropdown lists
   - Range validation rules

---

## Automated Testing in CI/CD

### GitHub Actions Example

```yaml
name: Excel Renderer Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - run: mvn test -Dtest=ExcelSectionRendererTest
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `Cannot get a STRING value from a NUMERIC cell` | Use `cell.getNumericCellValue()` or set format explicitly |
| `Sheet not found` | Verify sheet name in template and cell reference |
| `Template resource not found` | Check namespace path resolution |
| `Out of memory` | Increase heap: `mvn test -Xmx4g` |
| `NullPointerException in mapFieldValues` | Ensure `fieldMappings` or `fieldMappingGroups` is initialized |

---

## Checklist for Complete Testing

- [ ] Run unit tests: `mvn test -Dtest=ExcelSectionRendererTest`
- [ ] All 5 unit tests pass
- [ ] Start application: `mvn spring-boot:run`
- [ ] Test curl request with sample data
- [ ] Test different cell references (A1, Sheet1!B5, etc.)
- [ ] Test numeric and string values
- [ ] Test nested data structures
- [ ] Test error cases (missing template, invalid data)
- [ ] Check logs for proper error messages
- [ ] Verify workbook stored in RenderContext
- [ ] Run full build: `mvn clean compile test`
- [ ] No compilation warnings

---

## Next Steps

Once ExcelOutputService is implemented:
1. Download Excel files directly from API
2. Verify Excel formatting preserved
3. Test Excel-to-PDF conversion
4. Load and validate Excel output programmatically
