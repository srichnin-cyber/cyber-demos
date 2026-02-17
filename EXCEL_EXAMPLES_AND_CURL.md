# Excel Rendering - Comprehensive Examples & Curl Commands

## 1. Single Cell Mapping

**YAML:**
```yaml
sections:
  - sectionId: personal_info
    type: EXCEL
    templatePath: template.xlsx
    mappingType: JSONPATH
    fieldMappings:
      A1: "$.firstName"
      B1: "$.lastName"
      "Sheet2!C5": "$.email"
```

**Request:**
```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "personal-form",
    "data": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com"
    }
  }' > output.xlsx
```

---

## 2. Range Mapping (Column Fill)

Fill a column with array values from JSON.

**YAML:**
```yaml
fieldMappings:
  # Fill cells A2:A6 with values from array
  A2:A6: "$.itemNames"
  
  # Sheet-qualified range
  "Sheet1!B2:B6": "$.itemPrices"
```

**Data:**
```json
{
  "itemNames": ["Apple", "Banana", "Orange", "Grape", "Kiwi"],
  "itemPrices": [0.99, 0.59, 0.79, 1.29, 0.89]
}
```

**Result:**
```
A2: Apple       B2: 0.99
A3: Banana      B3: 0.59
A4: Orange      B4: 0.79
A5: Grape       B5: 1.29
A6: Kiwi        B6: 0.89
```

---

## 3. Row Mapping

Fill a row with array values.

**YAML:**
```yaml
fieldMappings:
  # Fill row 1 from column B to E
  "B1:E1": "$.headers"
```

**Data:**
```json
{
  "headers": ["Name", "Email", "Phone", "Department"]
}
```

**Result:** B1=Name, C1=Email, D1=Phone, E1=Department

---

## 4. Table Population (Repeating Group)

Populate multiple rows and columns from an array of objects. **Preferred approach for tables.**

**YAML:**
```yaml
fieldMappingGroups:
  - mappingType: JSONPATH
    basePath: "$.employees"
    repeatingGroup:
      startCell: "A2"        # Start at row 2, column A
      insertRows: false      # Don't shift existing rows
      overwrite: true        # Overwrite existing cell values
      maxItems: 50           # Max 50 rows
      fields:
        A: "employeeId"      # Column A <- each employee's ID
        B: "firstName"       # Column B <- each employee's first name
        C: "lastName"        # Column C <- each employee's last name
        D: "salary"          # Column D <- each employee's salary
```

**Data:**
```json
{
  "employees": [
    {"employeeId": "E001", "firstName": "Alice", "lastName": "Smith", "salary": 60000},
    {"employeeId": "E002", "firstName": "Bob", "lastName": "Johnson", "salary": 65000},
    {"employeeId": "E003", "firstName": "Carol", "lastName": "White", "salary": 70000}
  ]
}
```

**Result:**
```
Row 2: E001 | Alice     | Smith   | 60000
Row 3: E002 | Bob       | Johnson | 65000
Row 4: E003 | Carol     | White   | 70000
```

**Curl Command:**
```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "employee-roster",
    "data": {
      "employees": [
        {"employeeId": "E001", "firstName": "Alice", "lastName": "Smith", "salary": 60000},
        {"employeeId": "E002", "firstName": "Bob", "lastName": "Johnson", "salary": 65000}
      ]
    }
  }' > roster.xlsx
```

---

## 5. Table Population with Sheet Qualifier

**YAML:**
```yaml
fieldMappingGroups:
  - mappingType: JSONPATH
    basePath: "$.projects"
    repeatingGroup:
      startCell: "Sheet2!A5"    # Different sheet
      insertRows: true          # Insert rows, shifting existing down
      maxItems: 100
      fields:
        A: "projectName"
        B: "status"
        C: "budget"
```

**Data:**
```json
{
  "projects": [
    {"projectName": "Website Redesign", "status": "In Progress", "budget": 50000},
    {"projectName": "Mobile App", "status": "Planning", "budget": 100000}
  ]
}
```

---

## 6. Nested Data with Multiple Mapping Groups

Combine single cells and tables in one section.

**YAML:**
```yaml
fieldMappingGroups:
  # Group 1: Header information (single cells)
  - mappingType: JSONPATH
    fields:
      A1: "$.reportTitle"
      B1: "$.reportDate"

  # Group 2: Summary table (repeating group)
  - mappingType: JSONPATH
    basePath: "$.summary.metrics"
    repeatingGroup:
      startCell: "A5"
      fields:
        A: "metricName"
        B: "value"
```

**Data:**
```json
{
  "reportTitle": "Q1 Performance Report",
  "reportDate": "2026-03-31",
  "summary": {
    "metrics": [
      {"metricName": "Revenue", "value": 250000},
      {"metricName": "Profit", "value": 75000},
      {"metricName": "Growth", "value": "15%"}
    ]
  }
}
```

---

## 7. 2D Array (Matrix) Mapping

Fill a rectangular range from a 2D array.

**YAML:**
```yaml
fieldMappings:
  "A2:C4": "$.matrix"
```

**Data:**
```json
{
  "matrix": [
    [1, 2, 3],
    [4, 5, 6],
    [7, 8, 9]
  ]
}
```

**Result:**
```
    A  B  C
2:  1  2  3
3:  4  5  6
4:  7  8  9
```

---

## 8. Download Filled Excel Workbook

### Endpoint: `/api/documents/generate/excel`

**Request:**
```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "enrollment-form",
    "data": {
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com"
    }
  }' \
  --output filled-document.xlsx
```

**Response Headers:**
```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="document.xlsx"
```

**Response Body:** Binary XLSX file

---

## 9. Mixed Mapping Strategies

Use JSONPATH and DIRECT in same template.

**YAML:**
```yaml
fieldMappingGroups:
  # JSONPATH for complex queries
  - mappingType: JSONPATH
    fields:
      A1: "$.person[?(@.type=='PRIMARY')].name"

  # DIRECT for simple dot notation
  - mappingType: DIRECT
    fields:
      B1: "address.city"
```

---

## 10. Conditional Rendering

Use section conditions to conditionally populate tables.

**YAML:**
```yaml
sections:
  - sectionId: employees_if_available
    type: EXCEL
    templatePath: template.xlsx
    condition: "$.employees != null && $.employees.length > 0"
    fieldMappingGroups:
      - mappingType: JSONPATH
        basePath: "$.employees"
        repeatingGroup:
          startCell: "A2"
          columns:
            A: "name"
            B: "email"
```

**Data:** If `employees` array is null/empty, this section won't render.

---

## 11. Plan Comparison Tables (Pivot-Style Matrix)

Render a comparison table with benefits in rows and plans in columns with spacing between plan columns. Perfect for insurance plans, subscription tiers, feature comparison, etc.

**Data Input (Nested Plans):**
```json
{
  "comparisonTitle": "2026 Health Insurance Plans",
  "plans": [
    {
      "planName": "Basic",
      "benefits": [
        {"name": "Doctor Visits", "value": "$20 copay"},
        {"name": "ER Visit", "value": "$250 copay"},
        {"name": "Hospital", "value": "$1000 deductible"},
        {"name": "Prescription", "value": "$10/$35 copay"}
      ]
    },
    {
      "planName": "Premium",
      "benefits": [
        {"name": "Prescription", "value": "$5 copay"},
        {"name": "Doctor Visits", "value": "Covered 100%"},
        {"name": "ER Visit", "value": "$150 copay"},
        {"name": "Hospital", "value": "$500 deductible"}
      ]
    },
    {
      "planName": "Elite",
      "benefits": [
        {"name": "ER Visit", "value": "Covered 100%"},
        {"name": "Hospital", "value": "$250 deductible"},
        {"name": "Doctor Visits", "value": "Covered 100%"},
        {"name": "Prescription", "value": "Free"}
      ]
    }
  ]
}
```

**Transformation Step (Java):**
Use `PlanComparisonTransformer` to convert nested data to 2D matrix:

```java
// Somewhere in your controller/service
Map<String, Object> requestData = // ... parse incoming request

// Extract plans list
List<Map<String, Object>> plans = (List<Map<String, Object>>) requestData.get("plans");

// Transform and inject comparison matrix into data
Map<String, Object> templateData = PlanComparisonTransformer.injectComparisonMatrix(
    requestData,           // Original request data
    plans,                 // List of plans with benefits
    "name",                // Benefit name field
    "value",               // Benefit value field
    1                      // 1-column spacing between plan columns
);

// Now templateData contains: comparisonMatrix (2D array) + original fields
```

**Transformed Matrix:**
```json
{
  "comparisonMatrix": [
    ["Benefit", "", "Basic", "", "Premium", "", "Elite"],
    ["Doctor Visits", "", "$20 copay", "", "Covered 100%", "", "Covered 100%"],
    ["ER Visit", "", "$250 copay", "", "$150 copay", "", "Covered 100%"],
    ["Hospital", "", "$1000 deductible", "", "$500 deductible", "", "$250 deductible"],
    ["Prescription", "", "$10/$35 copay", "", "$5 copay", "", "Free"]
  ]
}
```

**YAML Template (plan-comparison.yaml):**
```yaml
templateId: plan-comparison
description: Plan comparison table with benefits vs plans
sections:
  - sectionId: plan_comparison
    type: EXCEL
    templatePath: comparison-template.xlsx
    mappingType: JSONPATH
    fieldMappings:
      # Map entire 2D matrix to sheet range
      "A1:G6": "$.comparisonMatrix"
```

**Curl Command:**
```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "plan-comparison",
    "data": {
      "comparisonMatrix": [
        ["Benefit", "", "Plan A", "", "Plan B", "", "Plan C"],
        ["Feature 1", "", "Yes", "", "Yes", "", "No"],
        ["Feature 2", "", "50", "", "100", "", "200"],
        ["Feature 3", "", "$10", "", "$15", "", "$25"]
      ]
    }
  }' --output comparison.xlsx
```

**Result in Excel:**
```
         A              B    C           D    E            F    G
1:   Benefit           -    Basic       -    Premium      -    Elite
2:   Doctor Visits     -    $20 copay   -    Covered 100% -    Covered 100%
3:   ER Visit          -    $250 copay  -    $150 copay   -    Covered 100%
4:   Hospital          -    $1000 ded*  -    $500 ded*    -    $250 ded*
5:   Prescription      -    $10/$35     -    $5 copay     -    Free
```

**Key Features:**
- ✅ Benefits in rows (auto-normalized for consistent matching)
- ✅ Plans in columns with configurable spacing
- ✅ Handles benefits in different order across plans
- ✅ Handles missing benefits gracefully (empty cells)
- ✅ Custom field names (e.g., "description" instead of "name")
- ✅ Configurable spacing width (1, 2, or more columns)

**Transformer Options:**

```java
// Option 1: Default (1-column spacing)
PlanComparisonTransformer.injectComparisonMatrix(data, plans);

// Option 2: 2-column spacing
PlanComparisonTransformer.injectComparisonMatrix(data, plans, "name", "value", 2);

// Option 3: Custom field names
PlanComparisonTransformer.injectComparisonMatrix(
    data,
    plans,
    "featureDescription",    // benefit name field
    "availability",          // benefit value field
    1                        // spacing
);

// Option 4: Just transform (without data injection)
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);
```

---

## API Summary

| Endpoint | Method | Purpose | Response |
|----------|--------|---------|----------|
| `/api/documents/generate` | `POST` | Generate PDF | PDF bytes |
| `/api/documents/generate/excel` | `POST` | Generate Excel | XLSX bytes |

**Request Body (same for both):**
```json
{
  "namespace": "common-templates",  // Optional, defaults to "common-templates"
  "templateId": "template-name",    // Required
  "data": { /* JSON data */ }       // Required
}
```

---

## Tips & Best Practices

1. **Column References**: Use letter (A, B, etc.) or sheet-qualified format (Sheet1!A2)
2. **Arrays in Tables**: Always use `repeatingGroup` with `startCell` for multi-row data
3. **Ranges**: Use `A2:A6` syntax for filling predetermined ranges with arrays
4. **Nested Data**: Leverage `basePath` to reduce filter/query overhead
5. **Overwrite Control**: Set `overwrite: false` to preserve existing template values
6. **Row Insertion**: Use `insertRows: true` to shift existing rows and add space
7. **Error Handling**: Invalid cell refs or missing data fields log warnings but don't fail
8. **Type Conversion**: Numeric strings are auto-converted; use templates with format cells for dates

---

## Example: Complete Real-World Scenario

**YAML: invoice-excel.yaml**
```yaml
templateId: invoice
description: Excel invoice with header, line items, and summary
pages:
  - id: invoice_page
    sections:
      # Header info
      - sectionId: header
        type: EXCEL
        templatePath: forms/invoice-template.xlsx
        fieldMappings:
          A1: "$.invoiceNumber"
          B1: "$.invoiceDate"
          C1: "$.dueDate"
          A2: "$.customerName"

      # Line items table
      - sectionId: line_items
        type: EXCEL
        templatePath: forms/invoice-template.xlsx
        fieldMappingGroups:
          - mappingType: JSONPATH
            basePath: "$.lineItems"
            repeatingGroup:
              startCell: "A5"
              fields:
                A: "description"
                B: "quantity"
                C: "unitPrice"
                D: "total"

      # Summary
      - sectionId: summary
        type: EXCEL
        templatePath: forms/invoice-template.xlsx
        fieldMappings:
          A15: "$.subtotal"
          B15: "$.tax"
          C15: "$.total"
```

**Curl:**
```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "invoice",
    "data": {
      "invoiceNumber": "INV-2026-001",
      "invoiceDate": "2026-02-17",
      "dueDate": "2026-03-17",
      "customerName": "Acme Corp",
      "lineItems": [
        {"description": "Consulting", "quantity": 10, "unitPrice": 150, "total": 1500},
        {"description": "Design", "quantity": 20, "unitPrice": 100, "total": 2000}
      ],
      "subtotal": 3500,
      "tax": 350,
      "total": 3850
    }
  }' --output invoice.xlsx
```

---

Done! Excel rendering now supports:
✅ Single cells  
✅ Column/row ranges  
✅ Multi-column table population  
✅ 2D matrix fills  
✅ Sheet qualification  
✅ Multiple mapping strategies  
✅ XLSX download
