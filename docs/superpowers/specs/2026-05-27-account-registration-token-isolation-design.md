# Account Registration and Token Isolation Design

## Goal

Add self-service accounts for AutoBookkeeper so invited users can register with a username and password, receive an automatically generated API token, and use the web dashboard or iPhone Shortcuts without seeing other users' transactions.

## Chosen approach

Use database-backed accounts plus API tokens.

Users register with:

- username
- password
- invite code

The server stores a BCrypt password hash, generates a random API token, assigns a stable owner key, and uses that owner key for transaction isolation. Existing environment-variable tokens remain supported for backward compatibility.

## Non-goals for the first version

- Email verification
- Password reset by email
- Admin user management UI
- Multiple invite codes
- Token rotation UI
- Session cookies or JWT authentication

These can be added later if the app grows beyond invited friends and personal use.

## Data model

Add an `AppUser` entity backed by an `app_users` table.

Fields:

- `id`: database primary key
- `username`: unique login name
- `passwordHash`: BCrypt hash of the password
- `ownerKey`: stable key used to isolate transactions
- `apiToken`: random token used by web requests and iPhone Shortcuts
- `createdAt`: creation timestamp

Uniqueness constraints:

- username must be unique
- ownerKey must be unique
- apiToken must be unique

Owner keys should not be user-editable. A generated owner key such as `user_<random>` avoids coupling transaction ownership to usernames.

## Configuration

Add one new property:

```yaml
autobookkeeper:
  invite-code: ""
```

Environment variable for Railway:

```text
AUTOBOOKKEEPER_INVITE_CODE=your-invite-code
```

Registration is allowed only when the provided invite code matches this configured value. If no invite code is configured, registration should fail with a clear server-side error instead of allowing public registration.

## API design

### Register

```http
POST /api/auth/register
Content-Type: application/json
```

Request:

```json
{
  "username": "friend1",
  "password": "password",
  "inviteCode": "invite-code"
}
```

Success response:

```json
{
  "username": "friend1",
  "token": "generated-api-token"
}
```

Validation:

- username is required
- username length should be reasonable
- password is required
- password should have a minimum length
- invite code must match configuration
- duplicate username returns a conflict response

### Login

```http
POST /api/auth/login
Content-Type: application/json
```

Request:

```json
{
  "username": "friend1",
  "password": "password"
}
```

Success response:

```json
{
  "username": "friend1",
  "token": "existing-api-token"
}
```

Invalid credentials return `401 Unauthorized` without revealing whether the username or password was wrong.

## Authentication flow

Current protected endpoints continue to require `X-API-Token`:

- `POST /api/process`
- `/api/transactions/**`

`UserTokenResolver` should resolve tokens in this order:

1. Database user `apiToken`
2. Configured multi-user tokens from `AUTOBOOKKEEPER_USER_TOKENS`
3. Legacy default token from `AUTOBOOKKEEPER_API_TOKEN`

This preserves existing deployments and existing iPhone Shortcuts while enabling new registered users.

`AuthenticatedUser` can continue to carry `ownerKey`. If the frontend needs the username later, a separate `/api/auth/me` endpoint can be added, but it is not required for the first version.

## Frontend design

Replace the token-only card with an account card that supports:

- Login form
- Register form
- Current logged-in status
- Copy token button for iPhone Shortcuts
- Logout button

On successful login or registration:

- Store token in `localStorage` using the existing token key
- Load the dashboard immediately
- Show the username and token copy action

Manual token entry can remain available as an advanced fallback so the app still works with legacy environment tokens.

## iPhone Shortcuts flow

No backend API change is required for shortcuts.

Users log in on the web page, copy their token, and set the shortcut request header:

```text
X-API-Token: user-token
```

The request body remains unchanged:

```json
{
  "imageBase64": "...",
  "source": "ios-shortcuts"
}
```

## Error handling

Registration errors:

- missing or invalid invite code: `403 Forbidden`
- duplicate username: `409 Conflict`
- invalid request fields: `400 Bad Request`
- invite code not configured: `503 Service Unavailable`

Login errors:

- invalid username or password: `401 Unauthorized`

Protected endpoint errors remain:

- missing or invalid `X-API-Token`: `401 Unauthorized`

## Security notes

- Never store plain-text passwords.
- Use BCrypt for password hashing.
- Generate tokens with `SecureRandom`.
- Do not log passwords or tokens.
- Keep invite registration disabled unless `AUTOBOOKKEEPER_INVITE_CODE` is configured.
- Existing legacy token support should remain to avoid locking out the current owner.

## Testing plan

Backend tests:

- Register succeeds with valid invite code and returns token.
- Register stores a BCrypt password hash, not the raw password.
- Duplicate username fails.
- Wrong invite code fails.
- Login succeeds with correct password.
- Login fails with wrong password.
- Database token resolves to the correct owner key.
- Registered users cannot see each other's transactions.
- Existing environment token behavior remains compatible.

Frontend verification:

- Register from the page saves token and loads dashboard.
- Login from the page saves token and loads dashboard.
- Logout clears token.
- Copy token works.

## Deployment

After implementation, Railway needs one new environment variable:

```text
AUTOBOOKKEEPER_INVITE_CODE=your-invite-code
```

Because `spring.jpa.hibernate.ddl-auto=update` is already used, the new `app_users` table should be created automatically on deployment.
