The purpose of collecting unique benefits from all plans is to **create a comprehensive unified list of all benefits that appear across ANY plan**, so that the comparison matrix includes every benefit from every plan.

### Why This Matters

Consider this scenario:

```
Plan A benefits:    Plan B benefits:
- Doctor Visits     - Doctor Visits
- Prescriptions     - Dental
- Dental            - Vision
```

**Without collecting unique benefits**, you'd miss some benefits in the matrix.

**After collecting unique benefits**, you get:
```
Unified list: ["Doctor Visits", "Prescriptions", "Dental", "Vision"]
```

### The Result

The final matrix shows **ALL benefits**, with plan-specific values filled in (and empty cells for benefits a plan doesn't offer):

```
[
  ["Benefit", "", "Plan A", "", "Plan B"],
  ["Doctor Visits", "", "✓", "", "✓"],
  ["Prescriptions", "", "✓", "", ""],        ← Plan B doesn't have this
  ["Dental", "", "✓", "", "✓"],
  ["Vision", "", "", "", "✓"]                ← Plan A doesn't have this
]
```

### Implementation Details

The code uses a `LinkedHashMap` to:
1. **Normalize benefit names** (`toLowerCase().trim()`) so "Doctor Visits", "doctor visits", "DOCTOR VISITS" all match
2. **Preserve insertion order** (LinkedHashMap feature) - benefits appear in the order they're first encountered
3. **Avoid duplicates** - `putIfAbsent()` ensures each benefit appears only once, using the first occurrence's casing as the display name

This approach ensures a **complete side-by-side comparison** where no benefit is hidden, even if plans list them in different orders or use slightly different casing.