# Account Registration Token Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build invite-code registration and password login that generates per-user API tokens and isolates transactions by database-backed owner keys.

**Architecture:** Add a database `AppUser` model and repository, an `AuthService` for registration/login/token generation, and an `AuthController` for public auth endpoints. Extend `UserTokenResolver` to resolve database tokens before existing environment tokens. Update the static dashboard to support login/register while preserving manual token fallback.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, H2/PostgreSQL via Hibernate `ddl-auto=update`, BCrypt from Spring Security Crypto, vanilla HTML/CSS/JS frontend, JUnit/MockMvc tests.

---

## File Structure

- Create `src/main/java/com/autobookkeeper/user/AppUser.java`: JPA entity for accounts.
- Create `src/main/java/com/autobookkeeper/user/AppUserRepository.java`: username/token lookup repository.
- Create `src/main/java/com/autobookkeeper/user/AuthService.java`: registration, login, token generation, password hashing.
- Create `src/main/java/com/autobookkeeper/api/dto/AuthRequest.java`: login request DTO.
- Create `src/main/java/com/autobookkeeper/api/dto/RegisterRequest.java`: register request DTO.
- Create `src/main/java/com/autobookkeeper/api/dto/AuthResponse.java`: auth response DTO.
- Create `src/main/java/com/autobookkeeper/api/AuthController.java`: `/api/auth/register` and `/api/auth/login`.
- Modify `src/main/java/com/autobookkeeper/config/AutoBookkeeperProperties.java`: add `inviteCode` property.
- Modify `src/main/java/com/autobookkeeper/security/UserTokenResolver.java`: resolve database `apiToken` before config tokens.
- Modify `src/main/resources/static/index.html`: account UI for login/register/copy/logout, keep manual token fallback.
- Create `src/test/java/com/autobookkeeper/api/AuthControllerTest.java`: registration/login behavior.
- Modify `src/test/java/com/autobookkeeper/api/TransactionControllerTest.java`: database-token user isolation.
- Modify existing property constructor call sites if needed to include compatibility constructor.

## Task 1: Backend account registration and login

**Files:**
- Create: `src/main/java/com/autobookkeeper/user/AppUser.java`
- Create: `src/main/java/com/autobookkeeper/user/AppUserRepository.java`
- Create: `src/main/java/com/autobookkeeper/user/AuthService.java`
- Create: `src/main/java/com/autobookkeeper/api/dto/AuthRequest.java`
- Create: `src/main/java/com/autobookkeeper/api/dto/RegisterRequest.java`
- Create: `src/main/java/com/autobookkeeper/api/dto/AuthResponse.java`
- Create: `src/main/java/com/autobookkeeper/api/AuthController.java`
- Modify: `src/main/java/com/autobookkeeper/config/AutoBookkeeperProperties.java`
- Test: `src/test/java/com/autobookkeeper/api/AuthControllerTest.java`

- [ ] Step 1: Write failing MockMvc tests for valid registration, duplicate username, wrong invite code, valid login, wrong password, and password hash not being raw password.
- [ ] Step 2: Run `mvn "-Dtest=AuthControllerTest" test` and verify tests fail because auth classes/endpoints do not exist.
- [ ] Step 3: Implement `AppUser`, repository, DTOs, `AuthService`, `AuthController`, and `inviteCode` property.
- [ ] Step 4: Run `mvn "-Dtest=AuthControllerTest" test` and verify all auth tests pass.
- [ ] Step 5: Commit with `feat(auth): add invite registration and login`.

## Task 2: Database token resolver integration

**Files:**
- Modify: `src/main/java/com/autobookkeeper/security/UserTokenResolver.java`
- Test: `src/test/java/com/autobookkeeper/api/TransactionControllerTest.java`

- [ ] Step 1: Write a failing test that creates two database users, creates transactions under each `ownerKey`, then verifies each `X-API-Token` only sees its own transactions.
- [ ] Step 2: Run `mvn "-Dtest=TransactionControllerTest" test` and verify the new test fails because database tokens are not resolved.
- [ ] Step 3: Inject `AppUserRepository` into `UserTokenResolver` and resolve `apiToken` to `AuthenticatedUser(ownerKey)` before environment tokens.
- [ ] Step 4: Run `mvn "-Dtest=TransactionControllerTest" test` and verify it passes.
- [ ] Step 5: Commit with `feat(auth): resolve database user tokens`.

## Task 3: Frontend login/register UI

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] Step 1: Update the token card to show login and registration forms, current logged-in username, copy-token, logout, and manual token fallback.
- [ ] Step 2: Add JavaScript functions `registerAccount`, `loginAccount`, `logoutAccount`, `copyToken`, and keep `saveToken` for manual fallback.
- [ ] Step 3: Verify manually by opening the app locally and using `/api/auth/register` then dashboard loading with saved token.
- [ ] Step 4: Commit with `feat(ui): add account login and registration controls`.

## Task 4: Full verification and deployment notes

**Files:**
- Modify: `src/main/resources/application.yml`
- Optionally modify: `docs/deploy-render-railway-fly.md`

- [ ] Step 1: Add default `autobookkeeper.invite-code` mapping in `application.yml` using `${AUTOBOOKKEEPER_INVITE_CODE:}`.
- [ ] Step 2: Run `mvn test` and verify full suite passes.
- [ ] Step 3: Run `git status --short` and ensure only intended files are committed.
- [ ] Step 4: Push commits with `git push`.
- [ ] Step 5: Set Railway env var `AUTOBOOKKEEPER_INVITE_CODE` before inviting users.

## Self-review

- Spec coverage: registration, login, token generation, owner isolation, legacy token compatibility, frontend token copy, and deployment env var are covered.
- Placeholder scan: no unresolved TBD/TODO placeholders are present.
- Type consistency: DTO names, property name `inviteCode`, repository token lookup, and resolver flow are consistent across tasks.
