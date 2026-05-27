# Transaction Type and Mobile Dashboard Design

## Goal

Make the AutoBookkeeper web dashboard usable from a phone at any time, and change transaction classification from a single flat category into a two-level model:

- `type`: `收入` or `支出`
- `category`: detailed category under that type

Amounts remain positive numbers. Income and expense are distinguished by `type`, not by negative amounts.

## Mobile usage

Users open the Railway site on iPhone Safari:

```text
https://autobookkeeper-production.up.railway.app/
```

They enter the API token once, then use Safari's "添加到主屏幕" action so the dashboard behaves like a lightweight app. The page remains a static Spring Boot frontend protected by the existing `X-API-Token` mechanism.

## Data model

Add a `TransactionType` enum:

```java
INCOME
EXPENSE
```

Expose it to API/UI as Chinese labels:

```text
收入
支出
```

`Transaction` gains a non-null `type` field. Existing rows are compatible by defaulting to `EXPENSE`/`支出` unless a known income category indicates income.

`Bill` gains a `type` field so AI recognition can pass the transaction type into `AccountingEngine`.

## Categories

Expense categories:

```text
餐饮
交通
购物
住房
医疗
娱乐
生活缴费
转账
其他
未分类
```

Income categories:

```text
工资
奖金
报销
退款
转账收入
其他收入
```

## AI extraction

The cloud vision prompt should request this JSON shape:

```json
{
  "date": "2026-05-27",
  "amount": "16.00",
  "merchant": "ru zi ni sa",
  "type": "支出",
  "category": "转账",
  "confidence": 0.9,
  "rawText": "扫码二维码付款-给 ru zi ni sa -16.00"
}
```

Rules:

- Payment, purchase, transfer to another person: `支出`
- Salary, bonus, reimbursement, refund, transfer received: `收入`
- If uncertain: default to `支出` and lower confidence
- Merchant should preserve visible payee names, including pinyin/English/nicknames such as `ru zi ni sa`

## API changes

Update DTOs:

- `ProcessImageResponse`: include `type`
- `TransactionResponse`: include `type`
- `UpdateTransactionRequest`: accept `type`
- `MonthlySummaryResponse`: include:
  - `incomeTotal`
  - `expenseTotal`
  - `balance`
  - grouped category summaries by type

List and detail endpoints remain the same. Optional filtering by type can be added to the frontend locally first; backend filtering is not required for the first implementation because the page already loads a month of transactions.

## Accounting behavior

`AccountingEngine` should resolve category within the transaction type. If an old or AI-provided category conflicts with the inferred type, type takes priority for summary display. Unknown or missing type defaults to `支出`.

## Frontend mobile design

The dashboard should be mobile-first:

```text
本月总览
收入：¥5000
支出：¥1320
结余：¥3680

筛选：月份 / 类型 / 细分分类 / 商户搜索

账单：
[支出] ru zi ni sa      -¥16.00
转账 · 2026-05-26 · 需复核

[收入] 公司工资         +¥5000.00
工资 · 2026-05-25 · 已处理
```

UI rules:

- Income amounts display with `+` and green tone
- Expense amounts display with `-` and warm/red tone
- Type selector appears in filter and edit modal
- Category selector changes according to selected type
- Existing token storage behavior remains unchanged

## Compatibility

Existing records are handled as expenses unless their category is an income category. No data deletion or destructive migration is required. H2 local development and PostgreSQL cloud deployment should continue to work with Hibernate schema update.

## Testing

Update or add tests for:

- AI JSON parsing with `type`
- Missing `type` defaults to expense
- AccountingEngine creates transactions with type
- Transaction API response includes type
- Update API can edit type and category
- Monthly summary computes income, expense, balance
- Frontend is manually verified on mobile viewport
