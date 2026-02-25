## **How Excel Template Values Are Set**

### **Step 1: Template Configuration (YAML)**

The YAML file specifies a **range mapping**:

```yaml
fieldMappings:
  "A1:G6": "$.comparisonMatrix"
```

This says: **Fill cells A1 through G6 with the data from `comparisonMatrix`**

---

### **Step 2: Data Transformation**

The `PlanComparisonTransformer` creates a 2D list of lists:

```java
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);

// Result:
[
  ["Benefit", "", "Basic", "", "Premium"],           // Row 1
  ["Doctor Visits", "", "$20 copay", "", "Covered 100%"],  // Row 2
  ["Prescriptions", "", "$10 copay", "", "$5 copay"]  // Row 3
]
```

---

### **Step 3: Range Filling Logic**

The `ExcelSectionRenderer.applyRangeMapping()` method (lines 545+) does the filling:

```java
// Parse the range "A1:G6" to get start (A1) and end (G6)
String[] parts = range.split(":");  // ["A1", "G6"]
String start = parts[0];             // "A1"  → Column A (0), Row 1 (0)
String end = parts[1];               // "G6"  → Column G (6), Row 6 (5)

// If value is a List of Lists (2D array), fill it row by row, column by column
if (value instanceof List) {
    List<?> list = (List<?>) value;
    
    for (int i = 0; i < list.size(); i++) {
        Object rowData = list.get(i);
        
        // If this row is also a List, fill columns
        if (rowData instanceof List) {
            List<?> rowList = (List<?>) rowData;
            for (int j = 0; j < rowList.size(); j++) {
                Object cellValue = rowList.get(j);
                
                // Calculate target cell: A1, B1, C1, ... A2, B2, ...
                int targetCol = startCol + j;
                int targetRow = startRow + i;
                
                // Set value at that cell
                setCellValueAt(sheet, targetRow, targetCol, cellValue);
            }
        }
    }
}
```

---

### **Step 4: Visual Representation**

**Before** (2D list):
```
[
  ["Benefit", "", "Basic", "", "Premium"],
  ["Doctor Visits", "", "$20 copay", "", "Covered 100%"],
  ["Prescriptions", "", "$10 copay", "", "$5 copay"]
]
```

**Gets written to Excel range A1:G6 as**:
```
     A             B      C           D      E              
1    Benefit       [empty] Basic      [empty] Premium      
2    Doctor Visits [empty] $20 copay  [empty] Covered 100%
3    Prescriptions [empty] $10 copay  [empty] $5 copay     
```

---

### **Key Points**

| Aspect | Details |
|--------|---------|
| **Range Format** | `A1:G6` or `SheetName!A1:G6` |
| **Data Structure** | 2D List: `List<List<Object>>` |
| **Fill Direction** | Left-to-right, top-to-bottom |
| **Column Spacing** | Handled by transformer (empty strings in matrix) |
| **Missing Values** | Filled with empty strings `""` |
| **Cell Sizing** | Cells auto-adjust to fit content |

---

### **Example with Values-Only**

For `plan-comparison-values-only`, the range is `B1:F6` (starts at column B):

```yaml
fieldMappings:
  "B1:F6": "$.comparisonMatrixValues"
```

The matrix excludes the benefit column:
```
[
  ["", "Basic", "", "Premium"],
  ["", "$20 copay", "", "Covered 100%"],
  ["", "$10 copay", "", "$5 copay"]
]
```

Gets written starting at **B1** (template already has benefit names in column A).