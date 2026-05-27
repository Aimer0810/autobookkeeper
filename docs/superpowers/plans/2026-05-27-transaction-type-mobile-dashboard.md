# Transaction Type Mobile Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add income/expense transaction type support and make the dashboard more useful on iPhone with monthly income, expense, and balance summaries.

**Architecture:** Add `TransactionType` as a first-class domain enum while keeping amounts positive. AI returns `type`, `AccountingEngine` persists it, API DTOs expose it, monthly summary splits income and expense, and the static dashboard renders a mobile-first overview with type/category filtering.

**Tech Stack:** Java 17, Spring Boot 3.3, Spring Data JPA, H2/PostgreSQL, Jackson, JUnit/MockMvc, static HTML/CSS/JavaScript.

---

## File Map

- Create `src/main/java/com/autobookkeeper/domain/TransactionType.java`: enum for `INCOME`/`EXPENSE`, Chinese labels, parsing helpers, category inference.
- Modify `src/main/java/com/autobookkeeper/domain/Bill.java`: add `TransactionType type` to AI result record.
- Modify `src/main/java/com/autobookkeeper/domain/Transaction.java`: add non-null `type`, constructor parameter, getter, update method parameter.
- Modify `src/main/java/com/autobookkeeper/accounting/AccountingEngine.java`: infer/default type and pass it to transactions.
- Modify `src/main/java/com/autobookkeeper/ai/CloudVisionServiceImpl.java`: prompt asks for `type`; parser reads `type`; review fallback defaults to expense.
- Modify OCR fallback services: add expense type to placeholder bills.
- Modify API DTOs: `ProcessImageResponse`, `TransactionResponse`, `UpdateTransactionRequest`, `MonthlySummaryResponse`.
- Modify controllers: `ProcessController`, `TransactionController`.
- Modify `src/main/resources/category_rules.properties`: align income/expense category rules.
- Modify `src/main/resources/static/index.html`: mobile-first totals, type/category filters, edit type selector, plus/minus amount display.
- Modify tests: accounting, AI service, process controller, transaction controller, composite AI tests.

---

### Task 1: Add TransactionType Domain Model

**Files:**
- Create: `src/main/java/com/autobookkeeper/domain/TransactionType.java`
- Modify: `src/main/java/com/autobookkeeper/domain/Bill.java`
- Modify: `src/main/java/com/autobookkeeper/domain/Transaction.java`
- Test: `src/test/java/com/autobookkeeper/accounting/AccountingEngineTest.java`

- [ ] **Step 1: Write failing AccountingEngine tests**

Add assertions that a bill with `TransactionType.INCOME` creates an income transaction and a bill without a type defaults to expense.

```java
@Test
void createsIncomeTransactionWhenBillTypeIsIncome() {
    Bill bill = new Bill(LocalDate.of(2026, 5, 25), new BigDecimal("5000.00"), "公司", TransactionType.INCOME, "工资", "raw", "{}", 0.95, false);

    Transaction transaction = engine.createTransaction(bill, "ios-shortcuts");

    assertThat(transaction.getType()).isEqualTo(TransactionType.INCOME);
    assertThat(transaction.getCategory()).isEqualTo("工资");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -Dtest=AccountingEngineTest test
```

Expected: compilation fails because `TransactionType` and new `Bill` constructor do not exist.

- [ ] **Step 3: Create TransactionType**

Create `src/main/java/com/autobookkeeper/domain/TransactionType.java`:

```java
package com.autobookkeeper.domain;

import java.util.Set;

public enum TransactionType {
    INCOME("收入"),
    EXPENSE("支出");

    private static final Set<String> INCOME_CATEGORIES = Set.of("工资", "奖金", "报销", "退款", "转账收入", "收入", "其他收入");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TransactionType fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return EXPENSE;
        }
        if ("收入".equals(value) || "INCOME".equalsIgnoreCase(value)) {
            return INCOME;
        }
        return EXPENSE;
    }

    public static TransactionType inferFromCategory(String category) {
        return INCOME_CATEGORIES.contains(category) ? INCOME : EXPENSE;
    }
}
```

- [ ] **Step 4: Update Bill and Transaction**

Update `Bill` record to include `TransactionType type` between `merchant` and `category`.

Update `Transaction`:

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private TransactionType type = TransactionType.EXPENSE;
```

Add constructor parameter after `merchant`, assign `this.type = type == null ? TransactionType.EXPENSE : type;`, add `getType()`, and update `update(...)` signature to accept `TransactionType type` and set it when non-null.

- [ ] **Step 5: Update AccountingEngine**

In `createTransaction`, compute:

```java
TransactionType type = bill.type() == null ? TransactionType.inferFromCategory(bill.category()) : bill.type();
```

Pass `type` to the `Transaction` constructor.

- [ ] **Step 6: Run AccountingEngine tests**

Run:

```powershell
mvn -Dtest=AccountingEngineTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/autobookkeeper/domain src/main/java/com/autobookkeeper/accounting/AccountingEngine.java src/test/java/com/autobookkeeper/accounting/AccountingEngineTest.java
git commit -m "Add transaction type domain model"
```

---

### Task 2: Update AI and Processing Flow

**Files:**
- Modify: `src/main/java/com/autobookkeeper/ai/CloudVisionServiceImpl.java`
- Modify: `src/main/java/com/autobookkeeper/ai/LocalOCRServiceImpl.java`
- Modify: `src/main/java/com/autobookkeeper/ai/TesseractOCRServiceImpl.java`
- Modify: `src/main/java/com/autobookkeeper/api/dto/ProcessImageResponse.java`
- Modify: `src/main/java/com/autobookkeeper/api/ProcessController.java`
- Test: `src/test/java/com/autobookkeeper/ai/CloudVisionServiceImplTest.java`
- Test: `src/test/java/com/autobookkeeper/api/ProcessControllerTest.java`

- [ ] **Step 1: Write/update failing AI parser test**

In `CloudVisionServiceImplTest`, update JSON parsing test to include `"type":"收入"` and assert:

```java
assertThat(bill.type()).isEqualTo(TransactionType.INCOME);
```

- [ ] **Step 2: Run targeted AI test**

```powershell
mvn -Dtest=CloudVisionServiceImplTest test
```

Expected: FAIL until parser supports `type`.

- [ ] **Step 3: Update CloudVisionServiceImpl**

Prompt fields become:

```text
date, amount, merchant, type, category, confidence, rawText
```

Parse type:

```java
TransactionType type = TransactionType.fromLabel(text(root, "type", "支出"));
return new Bill(date, amount, merchant, type, category, rawText, json, confidence, confidence < 0.75);
```

Fallback review bills use `TransactionType.EXPENSE`.

- [ ] **Step 4: Update OCR fallback bill constructors**

Both placeholder services return `TransactionType.EXPENSE`.

- [ ] **Step 5: Update ProcessImageResponse and ProcessController**

Add `String type` to `ProcessImageResponse`, and set it in controller response:

```java
type = transaction.getType().label()
```

- [ ] **Step 6: Run processing tests**

```powershell
mvn -Dtest=CloudVisionServiceImplTest,ProcessControllerTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/autobookkeeper/ai src/main/java/com/autobookkeeper/api src/test/java/com/autobookkeeper/ai src/test/java/com/autobookkeeper/api
git commit -m "Add transaction type to AI processing flow"
```

---

### Task 3: Update Transaction API and Monthly Summary

**Files:**
- Modify: `src/main/java/com/autobookkeeper/api/dto/TransactionResponse.java`
- Modify: `src/main/java/com/autobookkeeper/api/dto/UpdateTransactionRequest.java`
- Modify: `src/main/java/com/autobookkeeper/api/dto/MonthlySummaryResponse.java`
- Modify: `src/main/java/com/autobookkeeper/api/TransactionController.java`
- Test: `src/test/java/com/autobookkeeper/api/TransactionControllerTest.java`

- [ ] **Step 1: Update TransactionController tests**

Add `type` in fixture constructors and PATCH body. Assert list and update responses include:

```java
.andExpect(jsonPath("$.type").value("收入"))
```

Update summary test to create one income and two expense transactions, then assert:

```java
.andExpect(jsonPath("$.incomeTotal").value(5000.00))
.andExpect(jsonPath("$.expenseTotal").value(35.50))
.andExpect(jsonPath("$.balance").value(4964.50))
.andExpect(jsonPath("$.incomeCategorySummaries[0].category").value("工资"))
.andExpect(jsonPath("$.expenseCategorySummaries[0].category").value("餐饮"))
```

- [ ] **Step 2: Run TransactionControllerTest**

```powershell
mvn -Dtest=TransactionControllerTest test
```

Expected: FAIL until DTO/controller updates are implemented.

- [ ] **Step 3: Update DTOs**

`TransactionResponse` adds `String type` after `merchant` and maps `transaction.getType().label()`.

`UpdateTransactionRequest` adds `TransactionType type`.

`MonthlySummaryResponse` becomes:

```java
public record MonthlySummaryResponse(
        String month,
        BigDecimal incomeTotal,
        BigDecimal expenseTotal,
        BigDecimal balance,
        List<CategorySummary> incomeCategorySummaries,
        List<CategorySummary> expenseCategorySummaries
) {
    public record CategorySummary(String category, BigDecimal amount) {
    }
}
```

- [ ] **Step 4: Update TransactionController**

Patch calls:

```java
transaction.update(request.transactionDate(), request.amount(), request.merchant(), request.type(), request.category(), request.status());
```

Summary splits transactions by `TransactionType.INCOME` and `TransactionType.EXPENSE`, computes income total, expense total, and balance.

- [ ] **Step 5: Run TransactionControllerTest**

```powershell
mvn -Dtest=TransactionControllerTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/autobookkeeper/api src/test/java/com/autobookkeeper/api
git commit -m "Expose income expense summaries"
```

---

### Task 4: Update Frontend Mobile Dashboard

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add type/category constants**

Add JavaScript constants:

```javascript
const categoriesByType = {
  '支出': ['餐饮', '交通', '购物', '住房', '医疗', '娱乐', '生活缴费', '转账', '其他', '未分类'],
  '收入': ['工资', '奖金', '报销', '退款', '转账收入', '其他收入']
};
```

- [ ] **Step 2: Add type filter and edit selector**

Add a `typeFilter` select with `全部类型`, `收入`, `支出`.

Add `editType` select in modal and make `editCategory` options depend on selected type.

- [ ] **Step 3: Render mobile monthly overview**

Update `renderSummary(summary)` to show:

```text
收入
支出
结余
```

Use `summary.incomeTotal`, `summary.expenseTotal`, `summary.balance`.

- [ ] **Step 4: Render signed display amounts**

In transaction rows:

```javascript
const sign = transaction.type === '收入' ? '+' : '-';
const amountClass = transaction.type === '收入' ? 'income' : 'expense';
```

Display `[收入]` or `[支出]` beside category.

- [ ] **Step 5: Update local filtering and saveTransaction**

`renderTransactions()` filters by `typeFilter` and category. `saveTransaction()` sends `type`.

- [ ] **Step 6: Manual check**

Run local app and open mobile viewport. Verify token entry, month summary, type filter, category filter, edit modal, and list row display.

- [ ] **Step 7: Commit**

```powershell
git add src/main/resources/static/index.html
git commit -m "Improve mobile dashboard with income expense view"
```

---

### Task 5: Final Verification and Push

**Files:**
- All changed files.

- [ ] **Step 1: Run full tests**

```powershell
mvn test
```

Expected:

```text
Tests run: 21+; Failures: 0; Errors: 0
BUILD SUCCESS
```

- [ ] **Step 2: Check deployment config scripts**

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-render-runtime-config.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-render-db-url.ps1
```

Expected both pass.

- [ ] **Step 3: Inspect git status and recent commits**

```powershell
git status --short
git log --oneline -5
```

Expected no unintended untracked or modified files.

- [ ] **Step 4: Push**

```powershell
git push origin main
```

Expected GitHub accepts commits and Railway redeploys.

---

## Self-Review

- Spec coverage: mobile usage, type/category model, AI type extraction, API DTOs, monthly summary, frontend, compatibility, and tests are covered.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: domain enum is `TransactionType` with API labels `收入`/`支出`; DTOs expose labels as strings except update request accepts enum names from JSON. If Chinese labels are required for PATCH, controller should normalize string input in implementation.
