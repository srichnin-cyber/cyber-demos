# Plan Comparison Excel - Quick Reference

## One-Liner Usage

```java
Map<String, Object> enriched = PlanComparisonTransformer.injectComparisonMatrix(data, plans);
```

## What You Get

| Input | Output |
|-------|--------|
| Nested plans with benefits (any order) | 2D matrix (benefits × plans) |
| planName + benefits[] | comparisonMatrix (ready for Excel) |
| Original request fields | Preserved + matrix added |

## Matrix Structure

```
[
  ["Benefit", "", "Plan 1", "", "Plan 2", "", "Plan 3"],
  ["Benefit A", "", "Value", "", "Value", "", "Value"],
  ["Benefit B", "", "Value", "", "Value", "", "Value"]
]
```

- **Column A**: Benefit names
- **Columns B, D, F**: Spacing (empty)
- **Columns C, E, G**: Plan values

## Template Example

```yaml
templateId: plan-comparison
sections:
  - sectionId: comparison
    type: EXCEL
    templatePath: comparison-template.xlsx
    fieldMappings:
      "A1:G6": "$.comparisonMatrix"  # Fill range with matrix
```

## Transformer Variants

**Defaults (1 spacing, "name"/"value" fields):**
```java
PlanComparisonTransformer.injectComparisonMatrix(data, plans);
```

**Custom spacing (2 columns):**
```java
PlanComparisonTransformer.injectComparisonMatrix(data, plans, "name", "value", 2);
```

**Custom field names:**
```java
PlanComparisonTransformer.injectComparisonMatrix(
    data, plans, 
    "featureDesc",      // Benefit name field
    "featureValue",     // Benefit value field
    1                   // Spacing width
);
```

**Just get the matrix (no injection):**
```java
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);
```

## Data Format

**Input:**
```json
{
  "plans": [
    {
      "planName": "Plan A",
      "benefits": [
        {"name": "Benefit 1", "value": "Value A1"},
        {"name": "Benefit 2", "value": "Value A2"}
      ]
    }
  ]
}
```

**Features:**
- ✅ Benefits can be in different order per plan
- ✅ Some plans can have different benefits
- ✅ Missing benefits → empty cells
- ✅ Benefit names are normalized (case-insensitive)

## Curl Example

```bash
curl -X POST http://localhost:8080/api/documents/generate/excel \
  -H "Content-Type: application/json" \
  -d '{
    "namespace": "common-templates",
    "templateId": "plan-comparison",
    "data": { "comparisonMatrix": [[...]] }
  }' --output comparison.xlsx
```

## Files Created

| File | Purpose |
|------|---------|
| `PlanComparisonTransformer.java` | Core transformation logic |
| `PlanComparisonTransformerTest.java` | 5 unit tests with examples |
| `PlanComparisonExcelIntegrationTest.java` | E2E integration tests |
| `plan-comparison.yaml` | YAML template |
| `comparison-template.xlsx` | Excel template (6 rows × 7 cols) |
| `PLAN_COMPARISON_GUIDE.md` | Detailed guide |
| `EXCEL_EXAMPLES_AND_CURL.md` | Updated with Example 11 |

## Tests

Run all transformation tests:
```bash
mvn -Dtest=PlanComparisonTransformerTest test
```

Run integration tests (API endpoint):
```bash
mvn -Dtest=PlanComparisonExcelIntegrationTest test
```

## Excel Template Columns

- **A**: Benefit names
- **B**: Spacing
- **C**: Plan 1 values
- **D**: Spacing
- **E**: Plan 2 values
- **F**: Spacing
- **G**: Plan 3 values

Templates are pre-styled with headers and borders.

---

See `PLAN_COMPARISON_GUIDE.md` for complete walkthrough.
