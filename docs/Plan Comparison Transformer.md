## PlanComparisonTransformer Class Explanation

This utility class transforms nested plan data (multiple insurance plans with their benefits) into a **2D matrix format** optimized for Excel comparison tables.

### **Core Purpose**

Convert this structure:
```
plans with benefits in different orders
↓
2D comparison matrix with benefits in rows, plans in columns
```

### **How It Works**

#### **Main Method: `transformPlansToMatrix()`**

Takes plans like:
```java
[
  { planName: "Basic", benefits: [
      { name: "Doctor Visits", value: "$20 copay" },
      { name: "Prescriptions", value: "$10 copay" }
    ]
  },
  { planName: "Premium", benefits: [
      { name: "Doctor Visits", value: "Covered 100%" },
      { name: "Prescriptions", value: "$5 copay" }
    ]
  }
]
```

And produces:
```
[
  ["Benefit", "", "Basic", "", "Premium"],           // Header
  ["Doctor Visits", "", "$20 copay", "", "Covered 100%"],
  ["Prescriptions", "", "$10 copay", "", "$5 copay"]
]
```

#### **Step-by-Step Process:**

1. **Collect Unique Benefits** - Gathers all benefit names from all plans (normalized to lowercase for matching), preserving the first occurrence as display name
   
2. **Extract Plan Names** - Creates list of plan names in order

3. **Build Benefit Map** - Creates a lookup map for each plan mapping normalized benefit names → their values
   
4. **Build 2D Matrix**:
   - **Header row**: `["Benefit", "", "Plan1", "", "Plan2", ...]` with configurable spacing
   - **Data rows**: `[BenefitName, "", Value1, "", Value2, ...]`
   - Missing benefits default to empty string

### **Key Features**

| Feature | Details |
|---------|---------|
| **Flexible Field Names** | Supports custom field names via `benefitNameField` and `benefitValueField` parameters |
| **Spacing Control** | Configurable spacing columns (`spacingWidth`) between plan columns for readability |
| **Case-Insensitive Matching** | Benefits are normalized to handle "Doctor Visits", "doctor visits", etc. as the same |
| **Default Arguments** | Convenience methods with sensible defaults |

### **Two Variants**

1. **`transformPlansToMatrix()`** - Full matrix with benefit column included
   - Use when template needs complete matrix

2. **`transformPlansToMatrixValuesOnly()`** - Strips first column (benefit names)
   - Use when template already has benefit names, only needs values
   - Returns matrix without the "Benefit" header and benefit name columns

### **Integration Methods**

- **`injectComparisonMatrix()`** - Adds `comparisonMatrix` field to data for template rendering
- **`injectComparisonMatrixValuesOnly()`** - Adds `comparisonMatrixValues` field (values only)

### **Use Case Example**

Perfect for Excel templates where you want to:
- Compare multiple insurance plans side-by-side
- Handle benefits in any order (since class normalizes them)
- Place benefits in a 2D array formula for automatic layout
- Keep plan data dynamic while maintaining consistent matrix structure