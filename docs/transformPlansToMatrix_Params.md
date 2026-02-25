The `transformPlansToMatrix` method takes **4 parameters**:

### **1. `List<Map<String, Object>> plans`**
- **What it is**: The collection of insurance plans to compare
- **Structure**: Each plan is a Map containing:
  - `"planName"` key → the name of the plan (e.g., "Basic", "Premium")
  - `"benefits"` key → a List of benefit Maps
- **Example**:
```java
[
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
```

### **2. `String benefitNameField`**
- **What it is**: The key name in each benefit Map that holds the benefit's display name
- **Default**: `"name"`
- **Example**: If your benefit Map uses `"description"` instead of `"name"`, pass `"description"` here
```java
// If benefit looks like: { "description": "Doctor Visits", "value": "$20" }
transformPlansToMatrix(plans, "description", "value", 1)
```

### **3. `String benefitValueField`**
- **What it is**: The key name in each benefit Map that holds the benefit's value/description
- **Default**: `"value"`
- **Example**: If your benefit Map uses `"coverage"` instead of `"value"`, pass `"coverage"` here
```java
// If benefit looks like: { "name": "Doctor Visits", "coverage": "$20 copay" }
transformPlansToMatrix(plans, "name", "coverage", 1)
```

### **4. `int spacingWidth`**
- **What it is**: Number of empty columns to insert between each plan's values
- **Default**: `1`
- **Purpose**: For readability/visual separation in Excel
- **Example with `spacingWidth=1`**:
```
["Benefit", "", "Plan A", "", "Plan B"]
                  ↑ space      ↑ space
```
- **Example with `spacingWidth=2`**:
```
["Benefit", "", "", "Plan A", "", "", "Plan B"]
                     ↑↑ space      ↑↑ space
```

### **Convenience Method**

There's also a no-argument version using defaults:
```java
transformPlansToMatrix(plans)
// Equivalent to:
transformPlansToMatrix(plans, "name", "value", 1)
```