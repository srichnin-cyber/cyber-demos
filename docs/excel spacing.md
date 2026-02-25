### **1. New Parameter: `rowSpacingHeight`**

Added to all transformation methods:

```java
// Main method now takes both spacing parameters
public static List<List<Object>> transformPlansToMatrix(
        List<Map<String, Object>> plans,
        String benefitNameField,
        String benefitValueField,
        int columnSpacingWidth,    // Horizontal spacing (unchanged)
        int rowSpacingHeight)      // NEW: Vertical spacing between benefits
```

### **2. How It Works**

When `rowSpacingHeight > 0`, the matrix inserts empty rows after each benefit:

```java
// After adding a benefit row
matrix.add(row);

// Insert empty row(s) if configured
if (rowSpacingHeight > 0) {
    for (int s = 0; s < rowSpacingHeight; s++) {
        List<Object> spacerRow = new ArrayList<>();
        for (int c = 0; c < totalColumns; c++) {
            spacerRow.add("");  // Fill entire row with empty cells
        }
        matrix.add(spacerRow);
    }
}
```

### **3. Backward Compatible**

All convenience methods default to `rowSpacingHeight = 0`:

```java
// Old signature - still works, no row spacing
transformPlansToMatrix(plans, "name", "value", 1)
// Equivalent to:
transformPlansToMatrix(plans, "name", "value", 1, 0)
```

---

## **Usage Example**

### **Without Row Spacing** (default)
```java
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(
    plans, "name", "value", 1, 0
);
```

**Result**:
```
Benefit         Basic    Premium
Doctor Visits   $20      Free
Prescriptions   $10      $5
```

### **With 1 Row Spacing**
```java
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(
    plans, "name", "value", 1, 1  // <- rowSpacingHeight = 1
);
```

**Result**:
```
Benefit         Basic    Premium
Doctor Visits   $20      Free
[BLANK ROW]
Prescriptions   $10      $5
[BLANK ROW]
```

### **Injection Methods**

```java
// With row spacing
Map<String, Object> enriched = PlanComparisonTransformer.injectComparisonMatrix(
    data, plans, "name", "value", 1, 1  // columnSpacing=1, rowSpacing=1
);

// Values-only with spacing
Map<String, Object> enriched = PlanComparisonTransformer.injectComparisonMatrixValuesOnly(
    data, plans, "name", "value", 1, 1
);
```

---

## **Adjusting YAML for Row Spacing**

When you use row spacing, update your template mapping to accommodate extra rows:

```yaml
# Without row spacing: 1 header + 5 benefits = 6 rows
fieldMappings:
  "A1:G6": "$.comparisonMatrix"

# With 1 row spacing per benefit: 1 header + (5 benefits × 2) = 11 rows
fieldMappings:
  "A1:G11": "$.comparisonMatrix"

# With 2 row spacing per benefit: 1 header + (5 benefits × 3) = 16 rows
fieldMappings:
  "A1:G16": "$.comparisonMatrix"
```

The feature is now fully backward compatible and ready to use! ✓

Made changes.