---
name: clean-imports
description: Enforce clean imports and prohibit inline fully qualified package/class names in Kotlin code and tests
---

# Clean Imports & No Fully Qualified Names

When generating, modifying, or refactoring Kotlin source code or tests:

1. **Never use inline fully qualified names:**
    - Do not write `java.nio.ByteBuffer.allocate(...)`, `java.io.File(...)`, or
      `com.andryoga.safebox...` inline in expressions, variable types, function signatures, or
      layout plans.
2. **Always import types at the top of the file:**
    - Add explicit `import` statements at the file header (e.g., `import java.nio.ByteBuffer`).
    - Use the imported short name cleanly throughout the file.
3. **Preserve readability and consistency:**
    - Keep import lists clean and ordered without wildcard/star imports.
