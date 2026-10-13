# License Evaluation & Status

Status: **Pending Final Confirmation by Maintainers**  
Candidate Licenses: **Apache-2.0** (Recommended) | **MIT** | **GPL-3.0**

---

## 1. Evaluation Matrix

| License | Key Advantages | Key Drawbacks | Fit for ClassFlow |
| :--- | :--- | :--- | :--- |
| **Apache 2.0** *(Recommended)* | - Explicit patent grant & retaliation protection.<br>- Preserves trademark protection.<br>- Permits proprietary extensions/plugins while keeping core open.<br>- Highly compatible with modern cloud & mobile libraries. | - Slightly longer license text than MIT. | **Best fit**: Protects authors and contributors from patent trolling and trademark misuse while allowing university clubs and external contributors to build native adapters. |
| **MIT** | - Extremely concise and recognizable.<br>- Zero barrier to entry. | - No explicit patent grant.<br>- Low legal protection against bad-faith commercial re-packaging under closed source. | Acceptable, but offers less protection for a complex multi-platform codebase. |
| **GPL v3** | - Strong copyleft ensures all derivatives remain 100% open source.<br>- Prevents commercial monetization without source disclosure. | - Can conflict with specific proprietary App Store distribution policies.<br>- Blocks optional proprietary university backend bridges or enterprise campus plugins. | High risk for App Store ecosystem and campus plugin extensibility. |

---

## 2. Working Draft License (Apache License, Version 2.0)

Subject to project maintainer confirmation during Phase 10:

```text
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.
      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to use, reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      patent license to make, have made, use, offer to sell, sell, import,
      and otherwise transfer the Work.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, provided that You meet standard Apache 2.0 conditions.

   [Full Apache 2.0 text retained for reference]
```
