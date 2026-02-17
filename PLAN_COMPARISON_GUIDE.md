# Plan Comparison Excel Generation - Complete Example Guide

This guide demonstrates how to generate a plan comparison table in Excel, showing benefits across multiple plans with automatic data transformation.

## Scenario

You need to generate an Excel sheet that compares insurance plans side-by-side:
- **Rows**: Benefits (Doctor Visits, ER Visit, Hospital, Prescription)
- **Columns**: Plans (Basic, Premium, Elite) 
- **Values**: Coverage details for each benefit per plan
- **Spacing**: 1 empty column between plan columns for readability

## Step-by-Step Usage

### Step 1: Prepare Your Input Data

Your API receives request data with nested plans and benefits (can be in any order):

```json
{
  "comparisonTitle": "2026 Health Insurance Plans",
  "effectiveDate": "March 1, 2026",
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

### Step 2: Transform Data in Your Controller/Service

Use `PlanComparisonTransformer` to convert nested data into a 2D matrix:

```java
import com.example.demo.docgen.util.PlanComparisonTransformer;

// In your controller/service method:
@PostMapping("/api/comparison")
public ResponseEntity<byte[]> generateComparison(@RequestBody Map<String, Object> request) {
    
    // Extract plans from request
    List<Map<String, Object>> plans = (List<Map<String, Object>>) request.get("plans");
    
    // Transform and inject comparison matrix
    Map<String, Object> templateData = PlanComparisonTransformer.injectComparisonMatrix(
        request,              // Preserves original fields (comparisonTitle, effectiveDate)
        plans,                // List with planName and benefits[]
        "name",               // Benefit name field
        "value",              // Benefit value field
        1                     // 1-column spacing between plans
    );
    
    // Now templateData contains:
    // - Original fields: comparisonTitle, effectiveDate
    // - Generated: comparisonMatrix (2D array ready for Excel)
    
    // Send to document generator
    return generateExcel("common-templates", "plan-comparison", templateData);
}
```

### Step 3: Transformation Output

The transformer converts the nested structure into a 2D matrix:

```
Input (nested):
{
  "plans": [
    {"planName": "Basic", "benefits": [...]},
    {"planName": "Premium", "benefits": [...]},
    {"planName": "Elite", "benefits": [...]}
  ]
}

Output (matrix):
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

### Step 4: Use YAML Template

Your YAML template simply maps the 2D matrix to an Excel range:

**File: `src/main/resources/common-templates/templates/plan-comparison.yaml`**
```yaml
templateId: plan-comparison
description: Plan comparison table with benefits vs plans
sections:
  - sectionId: plan_comparison
    type: EXCEL
    templatePath: comparison-template.xlsx
    mappingType: JSONPATH
    fieldMappings:
      "A1:G6": "$.comparisonMatrix"
```

### Step 5: Excel Template Structure

The Excel template (`comparison-template.xlsx`) has:
- Row 1: Headers (initially: "Benefit", "", "Plan A", "", "Plan B", "", "Plan C")
- Rows 2-6: Data cells (will be overwritten by matrix)
- Column widths pre-configured for readability

When filled, looks like:
```
   A               B    C                D    E              F    G
1: Benefit         -    Basic            -    Premium        -    Elite
2: Doctor Visits   -    $20 copay        -    Covered 100%   -    Covered 100%
3: ER Visit        -    $250 copay       -    $150 copay     -    Covered 100%
4: Hospital        -    $1000 deductible -    $500 deductible -    $250 deductible
5: Prescription    -    $10/$35 copay    -    $5 copay       -    Free
```

## Complete Curl Example

```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "plan-comparison",
    "data": {
      "comparisonTitle": "2026 Health Insurance Plans",
      "effectiveDate": "March 1, 2026",
      "comparisonMatrix": [
        ["Benefit", "", "Basic", "", "Premium", "", "Elite"],
        ["Doctor Visits", "", "$20 copay", "", "Covered 100%", "", "Covered 100%"],
        ["ER Visit", "", "$250 copay", "", "$150 copay", "", "Covered 100%"],
        ["Hospital", "", "$1000 deductible", "", "$500 deductible", "", "$250 deductible"],
        ["Prescription", "", "$10/$35 copay", "", "$5 copay", "", "Free"]
      ]
    }
  }' --output comparison.xlsx
```

**Note**: If you're using the transformer, you don't manually create the matrix - it's generated automatically.

## Advanced: Custom Field Names

If your data uses different field names:

```java
// Your data uses: "featureDescription" and "isIncluded"
PlanComparisonTransformer.injectComparisonMatrix(
    data,
    plans,
    "featureDescription",    // Field that contains benefit name
    "isIncluded",            // Field that contains benefit value
    2                        // 2-column spacing instead of 1
);
```

## Advanced: Different Spacing Widths

```java
// 2-column spacing between plans
PlanComparisonTransformer.injectComparisonMatrix(data, plans, "name", "value", 2);

// Result: ["Benefit", "", "", "Plan A", "", "", "Plan B", "", "", "Plan C"]
```

## Key Benefits

✅ **Automatic Benefit Matching**: Benefits normalized and matched across plans even if in different order
✅ **Handles Missing Benefits**: Plans with missing benefits get empty cells
✅ **Preserves Original Data**: Transformation doesn't modify original request fields
✅ **Flexible Field Names**: Works with any benefit name/value field names
✅ **Configurable Spacing**: 1, 2, or more columns between plans
✅ **Type Safe**: Compiled Java utility with proper null handling

## Bonus: Just Transform Without Injection

If you want to transform without modifying the data:

```java
// Get just the matrix, don't modify data
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);

// You can then do whatever you want with it
System.out.println(matrix);  // Debug
addToMap(data, "matrix", matrix);  // Custom handling
```

## Testing

Run the test to see all transformation scenarios:

```bash
mvn -Dtest=PlanComparisonTransformerTest test
```

Test examples include:
- Basic transformation with 3 plans
- Benefits in different orders
- Missing benefits across plans
- Custom spacing widths
- Real-world insurance scenario

---

**Questions?** Check the unit tests for additional examples!
