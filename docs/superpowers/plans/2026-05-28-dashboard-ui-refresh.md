# Dashboard UI Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the AutoBookkeeper static dashboard into a clean, professional personal-finance interface without changing backend APIs.

**Architecture:** Keep the current Spring Boot static `index.html` entry point. Update the HTML structure, CSS design tokens, responsive layout, and generated transaction/summary markup while preserving existing element IDs and JavaScript function names.

**Tech Stack:** Spring Boot static resources, vanilla HTML/CSS/JavaScript, Maven verification.

---

### Task 1: Static Dashboard Visual Refresh

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Preserve behavior anchors**

Keep these IDs and handlers unchanged because JavaScript and backend flows rely on them: `authCard`, `notice`, `dashboardNotice`, `monthFilter`, `merchantSearch`, `transactionIdSearch`, `typeFilter`, `categoryFilter`, `summary`, `transactions`, `editorBackdrop`, `detailBackdrop`, and all existing `onclick`/`onchange` function names.

- [ ] **Step 2: Replace visual system**

Replace the existing decorative gradient-heavy CSS with a restrained system:

```css
:root {
  --bg: #f7f8fb;
  --surface: #ffffff;
  --text: #09090b;
  --muted: #667085;
  --line: #e4e7ec;
  --primary: #2563eb;
}
```

Apply a Swiss-style layout: clear header, constrained content width, 8-16px radii, consistent spacing, visible focus states, no layout-shifting hover effects, and responsive grids for mobile.

- [ ] **Step 3: Improve information hierarchy**

Make the dashboard header compact and product-like, render the account controls as actions, convert helper boxes into operational panels, keep the monthly summary above the transaction list, and make transaction rows easier to scan with merchant/date/status on the left and amount/actions on the right.

- [ ] **Step 4: Verify generated markup**

Update `loadSummary()` and `renderTransactions()` only as needed for CSS hooks. Keep fetch URLs, payload shapes, status values, and category update behavior unchanged.

- [ ] **Step 5: Run verification**

Run:

```powershell
mvn test
```

Expected: Maven exits with code 0 and existing backend tests pass.

- [ ] **Step 6: Manual smoke check**

Start the app locally with:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open `http://localhost:8080`, confirm the login screen renders, the dashboard layout is responsive, and no console-breaking HTML syntax is introduced.
