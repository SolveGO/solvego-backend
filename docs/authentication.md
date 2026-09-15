# Authentication: first-stage Refresh Sessions

## API contract

`POST /api/auth/login` keeps the `{ username, password }` JSON request and
`{ accessToken }` JSON response. It also issues `solvego_refresh` as an HttpOnly
cookie. A successful login replaces the session referenced by the previous cookie.

`POST /api/auth/refresh` accepts an empty JSON object and the Refresh cookie.
It returns `{ accessToken }`. No Access Token is required. A successful refresh
never sends a new Refresh cookie, writes the session, or extends its TTL.

`POST /api/auth/logout` accepts an empty JSON object and the Refresh cookie.
It deletes only that session and expires the cookie. Missing/already removed
sessions are an idempotent 204. A Redis failure is not reported as a successful
server logout; the client can retry with its existing cookie.

All three requests require an exact allowed `Origin`,
`X-SolveGO-CSRF: 1`, and `Content-Type: application/json`.
Browser requests use `credentials: "include"`. Missing/foreign/null origins,
missing custom headers, and non-JSON content types are rejected with 403 before
controller work. OPTIONS preflight is handled by CORS.

This is a browser-only custom-header/Origin CSRF defense, not a synchronizer-token
flow. Keep the allowed origin list narrow. Default session-based CSRF is disabled
because cookie endpoints use this explicit filter and general APIs use only Bearer
JWT authentication. A backend-hosted Swagger page or curl must also satisfy this
contract; do not add arbitrary origins just to bypass it.

## Redis

A cryptographically random 32-byte token is Base64URL encoded. Only its SHA-256
hash is used in the Redis key:

```
solvego:auth:refresh:<64-character SHA-256 hex>
```

The Redis String contains JSON (timestamps are UTC epoch seconds):

```json
{"userId":42,"createdAt":1800000000,"expiresAt":1800604800}
```

The key is created atomically with `SET ... EX 604800`. Lookup checks the stored
absolute expiry as well as Redis key existence. Each login has a separate key,
including multiple devices for one user. No user-wide index, rotation, used-token
history, access blacklist, or per-API Redis authentication lookup is implemented.
The Spring problem cache and its 10-minute TTL do not control these sessions.
`CACHE_TYPE=none` does not disable authentication storage.

General protected API requests parse and verify the Access JWT once, including
expiry, and store userId in SecurityContext. They do not query Refresh Redis.
Access expiry remains 600000 ms. Invalid/expired Access on protected APIs returns
401. Missing/expired Refresh returns 401 and clears the cookie. Redis connection
failure returns 503 without clearing the cookie. Authorization/CSRF errors are 403.

## Configuration

Local development: frontend `http://localhost:5173`, API `http://localhost:8080`.
Use `localhost` consistently rather than mixing it with `127.0.0.1`.

| Setting | local/test | prod |
|---|---|---|
| Cookie Domain | omitted (host-only) | omitted (host-only) |
| Cookie Path | `/api/auth` | `/api/auth` |
| HttpOnly | true | true |
| SameSite | Lax | None |
| Secure | false | true |
| Max-Age | 604800 seconds | 604800 seconds |
| CORS allowCredentials | true | true |
| CORS origins | localhost:5173 default | required `CORS_ALLOWED_ORIGINS` |

Production `CORS_ALLOWED_ORIGINS` must contain the actual HTTPS frontend origin,
without a trailing slash. Multiple exact origins may be comma separated. Empty
values, wildcards, paths, queries and credentials are rejected. No CloudFront
hostname is assumed. Deploy production with the `prod` Spring profile and HTTPS.

The frontend currently targets `https://solvego-api.duckdns.org` in its deployment
workflow. Cross-site cookie delivery can be blocked by browser third-party cookie
policy even with SameSite=None/Secure. Verify against the actual deployed frontend;
use a same-site or same-origin deployment if necessary. Domain/Path is not an XSS
security boundary. HttpOnly limits cookie extraction, not malicious script actions.

Redis access restrictions, persistence, and eviction policy are operational
prerequisites for reliable sessions. Existing Compose Redis networking/persistence
has not been changed by this feature. Loss of Redis session state requires login.

## Tests and limitations

Run `./gradlew test`. Existing integration tests need the test MySQL and Redis
configured through TEST_DB_* and REDIS_*; Testcontainers tests also need Docker.
`RefreshAuthenticationIntegrationTest` uses an isolated Redis container to verify
hash storage, TTL, non-rotation, revocation, device separation, CSRF/CORS and no
Refresh Redis calls for normal API authentication.

Logout blocks future refresh validation. Already-issued Access tokens remain valid
for up to ten minutes, including an in-flight refresh validated before logout.
Refresh tokens are reusable until revoked or their absolute seven-day expiry;
reuse detection is deliberately out of scope.
