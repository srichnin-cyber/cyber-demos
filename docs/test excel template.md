Test `PlanComparisonTransformer` with Excel templates:

## **Step 1: Unit Test the Transformer**

```bash
mvn test -Dtest=*PlanComparisonTransformer*
```

Or run tests manually with sample data:

```java
// Create test data
List<Map<String, Object>> plans = Arrays.asList(
    createPlan("Basic", Arrays.asList(
        createBenefit("Doctor Visits", "$20 copay"),
        createBenefit("Prescriptions", "$10 copay")
    )),
    createPlan("Premium", Arrays.asList(
        createBenefit("Doctor Visits", "Covered 100%"),
        createBenefit("Prescriptions", "$5 copay")
    ))
);

// Transform to matrix
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(plans);

// Verify output
System.out.println("Matrix: " + matrix);
// Expected:
// [[Benefit, , Basic, , Premium],
//  [Doctor Visits, , $20 copay, , Covered 100%],
//  [Prescriptions, , $10 copay, , $5 copay]]
```

## **Step 2: Integration Test - Generate Excel and Validate**

Check the ExcelGenerationComprehensiveTest.java for complete examples:

```bash
mvn test -Dtest=ExcelGenerationComprehensiveTest
```

Key tests include:
- **Test 1**: Simple 3-plan comparison
- **Test 2**: Different benefit orders
- **Test 3**: Auto-transformation of raw plan data
- **Test 4**: Missing benefits handling
- **Test 5**: Custom matrix (skip transformation)

## **Step 3: Manual Testing via API**

### **A. Test with Full Matrix (includes benefit names)**

**Template**: `plan-comparison`

**Request**:
```bash
curl -X POST http://localhost:8080/api/generate \
  -H "Content-Type: application/json" \
  -d @request.json \
  --output comparison.xlsx
```

**request.json**:
```json
{
  "template": "plan-comparison",
  "data": {
    "comparisonTitle": "Insurance Plan Comparison",
    "plans": [
      {
        "planName": "Basic",
        "benefits": [
          { "name": "Doctor Visits", "value": "$20 copay" },
          { "name": "Prescriptions", "value": "$10 copay" }
        ]
      },
      {
        "planName": "Premium",
        "benefits": [
          { "name": "Doctor Visits", "value": "Covered 100%" },
          { "name": "Prescriptions", "value": "$5 copay" }
        ]
      }
    ]
  }
}
```

**Expected Excel Output** (cells A1:G3):
```
| Benefit       | [empty] | Basic         | [empty] | Premium      |
| Doctor Visits | [empty] | $20 copay     | [empty] | Covered 100% |
| Prescriptions | [empty] | $10 copay     | [empty] | $5 copay     |
```

### **B. Test with Values-Only Matrix (benefit names in template)**

**Template**: `plan-comparison-values-only`

**request.json**: (same as above)

**Expected Excel Output** (cells B1:F3):
```
| [empty] | Basic         | [empty] | Premium      |
| [empty] | $20 copay     | [empty] | Covered 100% |
| [empty] | $10 copay     | [empty] | $5 copay     |
```

(Benefit names left blank for template to fill in)

## **Step 4: Verify Output in Excel**

After generating, open the Excel file and check:

1. ✓ **Header row**: Plan names visible with proper spacing
2. ✓ **Data rows**: Benefits and values properly mapped
3. ✓ **Spacing**: Empty columns between plans visible
4. ✓ **Missing benefits**: Filled with empty strings (not errors)
5. ✓ **Cell alignment**: Data in correct cell ranges (A1:G6 or B1:F6)

## **Step 5: Test Edge Cases**

```bash
# Test 1: Benefits in different orders
# Plans list benefits as: Basic=[A, B, C], Premium=[B, C, A]
# Expected: Matrix should include all [A, B, C] in order encountered

# Test 2: Different field names
List<List<Object>> matrix = PlanComparisonTransformer.transformPlansToMatrix(
    plans, 
    "description",  // custom field name for benefit
    "coverage",     // custom field name for value
    2               // 2 empty columns spacing
);

# Test 3: No benefits/empty plans
List<List<Object>> empty = PlanComparisonTransformer.transformPlansToMatrix(
    Collections.emptyList()
);
// Expected: empty list
```

## **Step 6: Using Test Scripts**

The project includes helper scripts:

```bash
# Test plan comparison feature
./test-plans-summary.sh

# Or build and test all
mvn clean test -Dtest=ExcelGeneration*
```

## **Step 7: Debug Generated Data**

Add logging to see the matrix before Excel rendering:

```java
Map<String, Object> data = new HashMap<>();
data.put("title", "My Comparison");

List<Map<String, Object>> plans = /* your plans */;
Map<String, Object> enriched = PlanComparisonTransformer.injectComparisonMatrix(data, plans);

// Debug output
System.out.println("Comparison Matrix: " + enriched.get("comparisonMatrix"));
// Shows: [[Benefit, , Plan1, , Plan2], [Benefit1, , Value1, , Value2], ...]
```

**Complete test examples**: ExcelGenerationComprehensiveTest.java (lines 50-150)

**Template configuration**: EXCEL_PLAN_COMPARISON_DOCS.md