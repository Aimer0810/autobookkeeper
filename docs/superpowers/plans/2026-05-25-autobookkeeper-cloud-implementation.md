# AutoBookkeeper Cloud Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable Java Spring Boot AutoBookkeeper skeleton that lets iPhone Shortcuts upload payment screenshots, extracts bills through AI/OCR services, categorizes transactions, stores them, and exposes REST/Web UI access.

**Architecture:** The project is a Maven Spring Boot monolith with clear service boundaries: API controllers validate requests, AI services extract `Bill` data, `AccountingEngine` turns bills into `Transaction` entities, repositories persist data, and static resources provide a simple Web UI. Runtime behavior is controlled by `local`, `cloud`, and `home` Spring profiles.

**Tech Stack:** Java 17 LTS, Spring Boot 3.3.x, Spring Web, Spring Data JPA, Validation, H2 for local development, PostgreSQL for cloud, JUnit 5, Maven.

---

## File Structure

- `pom.xml`: Maven dependencies, Java version, Spring Boot plugin.
- `src/main/java/com/autobookkeeper/AutoBookkeeperApplication.java`: Spring Boot entry point.
- `src/main/java/com/autobookkeeper/config/AutoBookkeeperProperties.java`: typed app configuration for API token, AI provider, privacy settings.
- `src/main/java/com/autobookkeeper/security/ApiTokenFilter.java`: validates `X-API-Token` for protected API routes.
- `src/main/java/com/autobookkeeper/api/ProcessController.java`: handles screenshot processing requests.
- `src/main/java/com/autobookkeeper/api/TransactionController.java`: exposes transaction query APIs.
- `src/main/java/com/autobookkeeper/api/HealthController.java`: lightweight health check.
- `src/main/java/com/autobookkeeper/api/dto/ProcessImageRequest.java`: request DTO for Base64 image uploads.
- `src/main/java/com/autobookkeeper/api/dto/ProcessImageResponse.java`: response DTO for created transaction and review status.
- `src/main/java/com/autobookkeeper/api/dto/TransactionResponse.java`: response DTO for transaction queries.
- `src/main/java/com/autobookkeeper/ai/AIService.java`: image-to-bill service contract.
- `src/main/java/com/autobookkeeper/ai/CloudVisionServiceImpl.java`: cloud Vision API implementation using `{{API_KEY}}`-compatible config.
- `src/main/java/com/autobookkeeper/ai/TesseractOCRServiceImpl.java`: offline OCR fallback stub with deterministic behavior.
- `src/main/java/com/autobookkeeper/ai/LocalOCRServiceImpl.java`: local model extension stub.
- `src/main/java/com/autobookkeeper/ai/CompositeAIService.java`: orchestrates cloud-first and fallback behavior.
- `src/main/java/com/autobookkeeper/accounting/AccountingEngine.java`: categorizes bills and creates transactions.
- `src/main/java/com/autobookkeeper/accounting/CategoryRuleLoader.java`: loads `category_rules.properties`.
- `src/main/java/com/autobookkeeper/domain/Bill.java`: extracted bill value object.
- `src/main/java/com/autobookkeeper/domain/ProcessingStatus.java`: processing state enum.
- `src/main/java/com/autobookkeeper/domain/Transaction.java`: JPA transaction entity.
- `src/main/java/com/autobookkeeper/repository/TransactionRepository.java`: JPA repository.
- `src/main/resources/application.yml`: default, local, cloud, and home profile config.
- `src/main/resources/category_rules.properties`: initial Chinese merchant classification rules.
- `src/main/resources/static/index.html`: minimal mobile-friendly Web UI.
- `README.md`: project overview, quick start, environment variables.
- `docs/iphone-integration-guide.md`: iOS Shortcuts integration guide.
- `docs/deploy-render-railway-fly.md`: cloud deployment guide.
- `docs/deploy-vercel-frontend.md`: explains Vercel as optional frontend only.
- `src/test/java/com/autobookkeeper/accounting/AccountingEngineTest.java`: classification tests.
- `src/test/java/com/autobookkeeper/ai/CloudVisionServiceImplTest.java`: JSON extraction tests.
- `src/test/java/com/autobookkeeper/api/ProcessControllerTest.java`: API auth and validation tests.

---

### Task 1: Maven Project Foundation

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/autobookkeeper/AutoBookkeeperApplication.java`
- Create: `src/main/resources/application.yml`

- [ ] **Step 1: Create Maven build file**

Create `pom.xml` with Spring Boot parent, Java 17 LTS, web, JPA, validation, H2, PostgreSQL, test dependencies, and Spring Boot Maven plugin.

- [ ] **Step 2: Create application entry point**

Create `AutoBookkeeperApplication` in package `com.autobookkeeper` with a standard `SpringApplication.run(...)` main method.

- [ ] **Step 3: Create profile configuration**

Create `application.yml` with default `local` profile, multipart/request size limits, `autobookkeeper.api-token`, `autobookkeeper.ai.provider`, `autobookkeeper.ai.api-key`, `autobookkeeper.ai.timeout-ms`, and privacy defaults.

- [ ] **Step 4: Verify project compiles**

Run: `mvn -q -DskipTests compile`

Expected: build succeeds.

---

### Task 2: Domain Model and Repository

**Files:**
- Create: `src/main/java/com/autobookkeeper/domain/Bill.java`
- Create: `src/main/java/com/autobookkeeper/domain/ProcessingStatus.java`
- Create: `src/main/java/com/autobookkeeper/domain/Transaction.java`
- Create: `src/main/java/com/autobookkeeper/repository/TransactionRepository.java`

- [ ] **Step 1: Create `Bill` value object**

Fields: `LocalDate date`, `BigDecimal amount`, `String merchant`, `String category`, `String rawText`, `String structuredJson`, `double confidence`, `boolean needsReview`.

- [ ] **Step 2: Create `ProcessingStatus` enum**

Values: `PROCESSED`, `NEEDS_REVIEW`, `FAILED`.

- [ ] **Step 3: Create `Transaction` JPA entity**

Fields: `id`, `transactionDate`, `amount`, `merchant`, `category`, `rawText`, `structuredJson`, `confidence`, `status`, `source`, `createdAt`.

- [ ] **Step 4: Create repository**

Create `TransactionRepository extends JpaRepository<Transaction, Long>` with method `Page<Transaction> findAllByOrderByTransactionDateDescCreatedAtDesc(Pageable pageable)`.

- [ ] **Step 5: Verify compile**

Run: `mvn -q -DskipTests compile`

Expected: build succeeds.

---

### Task 3: Configuration and API Token Security

**Files:**
- Create: `src/main/java/com/autobookkeeper/config/AutoBookkeeperProperties.java`
- Create: `src/main/java/com/autobookkeeper/security/ApiTokenFilter.java`
- Modify: `src/main/java/com/autobookkeeper/AutoBookkeeperApplication.java`

- [ ] **Step 1: Add typed properties**

Create `AutoBookkeeperProperties` annotated with `@ConfigurationProperties(prefix = "autobookkeeper")` containing nested `Ai` and `Privacy` records/classes.

- [ ] **Step 2: Enable configuration properties**

Annotate the application class with `@ConfigurationPropertiesScan`.

- [ ] **Step 3: Add `ApiTokenFilter`**

Filter `/api/process` requests. If configured token is blank in `local`, allow request. If token is configured, require exact `X-API-Token` match. On mismatch return HTTP 401 with JSON body `{"error":"Unauthorized"}`.

- [ ] **Step 4: Add controller test later in Task 8**

Security behavior is verified after controllers exist.

---

### Task 4: AI/OCR Service Layer

**Files:**
- Create: `src/main/java/com/autobookkeeper/ai/AIService.java`
- Create: `src/main/java/com/autobookkeeper/ai/CloudVisionServiceImpl.java`
- Create: `src/main/java/com/autobookkeeper/ai/TesseractOCRServiceImpl.java`
- Create: `src/main/java/com/autobookkeeper/ai/LocalOCRServiceImpl.java`
- Create: `src/main/java/com/autobookkeeper/ai/CompositeAIService.java`
- Test: `src/test/java/com/autobookkeeper/ai/CloudVisionServiceImplTest.java`

- [ ] **Step 1: Write JSON parsing test**

Test that a model response containing `date`, `amount`, `merchant`, `category`, and `confidence` is converted into `Bill`.

- [ ] **Step 2: Create service contract**

`AIService` exposes `Bill extractBillFromImage(byte[] imageData)`.

- [ ] **Step 3: Implement cloud service skeleton**

`CloudVisionServiceImpl` should build a request shape suitable for a Vision API and parse JSON responses. The real API key must come from config and default to `{{API_KEY}}`. If the key is blank or still `{{API_KEY}}`, return a low-confidence review bill rather than making a real external call.

- [ ] **Step 4: Implement OCR fallbacks**

`TesseractOCRServiceImpl` and `LocalOCRServiceImpl` return review-required bills with explanatory `rawText`, giving users deterministic behavior until real OCR dependencies are configured.

- [ ] **Step 5: Implement composite service**

`CompositeAIService` chooses provider based on config and falls back to OCR if cloud extraction throws or returns low confidence.

- [ ] **Step 6: Run AI tests**

Run: `mvn -q -Dtest=CloudVisionServiceImplTest test`

Expected: tests pass.

---

### Task 5: Accounting and Classification Engine

**Files:**
- Create: `src/main/java/com/autobookkeeper/accounting/CategoryRuleLoader.java`
- Create: `src/main/java/com/autobookkeeper/accounting/AccountingEngine.java`
- Create: `src/main/resources/category_rules.properties`
- Test: `src/test/java/com/autobookkeeper/accounting/AccountingEngineTest.java`

- [ ] **Step 1: Write classification tests**

Test that merchants containing `麦当劳` become `餐饮`, `滴滴` becomes `交通`, and unknown merchants become AI category or `未分类`.

- [ ] **Step 2: Create category rules file**

Add rules for `餐饮`, `交通`, `购物`, `生活缴费`, `娱乐`, `医疗`, and `收入`.

- [ ] **Step 3: Implement rule loader**

Load properties from classpath, split comma-separated keywords, trim whitespace, and expose `Map<String, List<String>>`.

- [ ] **Step 4: Implement accounting engine**

`createTransaction(Bill bill, String source)` applies rule category, sets status `NEEDS_REVIEW` if bill needs review or confidence below `0.75`, otherwise `PROCESSED`.

- [ ] **Step 5: Run accounting tests**

Run: `mvn -q -Dtest=AccountingEngineTest test`

Expected: tests pass.

---

### Task 6: REST API Controllers and DTOs

**Files:**
- Create: `src/main/java/com/autobookkeeper/api/dto/ProcessImageRequest.java`
- Create: `src/main/java/com/autobookkeeper/api/dto/ProcessImageResponse.java`
- Create: `src/main/java/com/autobookkeeper/api/dto/TransactionResponse.java`
- Create: `src/main/java/com/autobookkeeper/api/ProcessController.java`
- Create: `src/main/java/com/autobookkeeper/api/TransactionController.java`
- Create: `src/main/java/com/autobookkeeper/api/HealthController.java`
- Test: `src/test/java/com/autobookkeeper/api/ProcessControllerTest.java`

- [ ] **Step 1: Create DTOs**

`ProcessImageRequest` has `@NotBlank String imageBase64` and `String source`. Responses expose transaction ID, status, merchant, amount, category, confidence, and review flag.

- [ ] **Step 2: Implement process endpoint**

`POST /api/process` decodes Base64, calls `AIService`, calls `AccountingEngine`, saves transaction, and returns response.

- [ ] **Step 3: Implement query endpoints**

`GET /api/transactions` returns paged transaction responses. `GET /api/transactions/{id}` returns one transaction or 404.

- [ ] **Step 4: Implement health endpoint**

`GET /api/health` returns `{"status":"UP"}`.

- [ ] **Step 5: Write API tests**

Verify missing token returns 401 when token is configured, invalid Base64 returns 400, and valid request returns 200 with persisted transaction.

- [ ] **Step 6: Run API tests**

Run: `mvn -q -Dtest=ProcessControllerTest test`

Expected: tests pass.

---

### Task 7: Static Web UI

**Files:**
- Create: `src/main/resources/static/index.html`

- [ ] **Step 1: Create mobile-friendly UI**

Single HTML file fetches `/api/transactions`, displays transaction date, merchant, amount, category, and status. Include an API token input stored in browser local storage and sent as `X-API-Token`.

- [ ] **Step 2: Verify with app run**

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

Expected: opening `http://localhost:8080` shows the dashboard and no JavaScript syntax errors.

---

### Task 8: Documentation

**Files:**
- Create: `README.md`
- Create: `docs/iphone-integration-guide.md`
- Create: `docs/deploy-render-railway-fly.md`
- Create: `docs/deploy-vercel-frontend.md`

- [ ] **Step 1: Create README**

Include project purpose, quick start, environment variables, profiles, API examples, and privacy defaults.

- [ ] **Step 2: Create iPhone guide**

Explain iOS Shortcut: receive image from share sheet, Base64 encode, POST JSON to `/api/process`, add `X-API-Token`, show response.

- [ ] **Step 3: Create cloud deployment guide**

Explain Render/Railway/Fly.io deployment using `mvn clean package`, Java 17 LTS, environment variables `AUTOBOOKKEEPER_API_TOKEN`, `VISION_API_KEY`, `DATABASE_URL`, and `SPRING_PROFILES_ACTIVE=cloud`.

- [ ] **Step 4: Create Vercel note**

Explain Vercel is optional frontend-only hosting and not the Java backend runtime.

---

### Task 9: Full Verification

**Files:**
- Modify if needed: files from previous tasks only.

- [ ] **Step 1: Run full tests**

Run: `mvn test`

Expected: all tests pass.

- [ ] **Step 2: Run package build**

Run: `mvn -q -DskipTests package`

Expected: jar builds under `target/`.

- [ ] **Step 3: Start local app**

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`

Expected: app starts on port `8080`.

- [ ] **Step 4: Smoke test health endpoint**

Run: `Invoke-RestMethod http://localhost:8080/api/health`

Expected: response contains `status: UP`.

---

## Self-Review

- Spec coverage: The plan covers cloud-first deployment, iPhone Shortcuts upload, Java Spring Boot backend, AI/OCR abstractions, accounting rules, persistence, Web UI, security, documentation, and tests.
- Placeholder scan: The plan intentionally uses `{{API_KEY}}` only as the required external AI key placeholder. No undefined implementation placeholders are part of the deliverable.
- Type consistency: `Bill`, `Transaction`, `AIService.extractBillFromImage(byte[])`, `AccountingEngine.createTransaction(Bill, String)`, and API DTO names are consistent across tasks.
