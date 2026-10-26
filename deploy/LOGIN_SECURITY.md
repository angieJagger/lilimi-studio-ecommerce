# Request rate limiting

Rate limits use persistent PostgreSQL counters in `request_rate_limits`.
Migration V28 renames the table created by V27 without removing counters.

## Limits

| Operation | Scope | Limit | Window |
|---|---|---:|---:|
| Login | Normalized email | 5 attempts | 5 minutes |
| Login | Source address | 30 attempts | 5 minutes |
| Inquiry | Source address | 5 attempts | 15 minutes |
| Registration | Source address | 10 attempts | 1 hour |
| Order creation | Source address | 10 created orders | 15 minutes |

Login limits are configurable through:

- `lilimi.security.login.account-attempts`
- `lilimi.security.login.source-attempts`
- `lilimi.security.login.window-seconds`

Public-form limits are defined in `PublicFormRateLimiter`.

## Behaviour

Login, inquiry and registration attempts consume their budget after CSRF
validation and before authentication or controller validation.

Order creation checks its budget after matching the idempotency key,
request content and customer ownership. Replaying a saved submission
returns its existing order without consuming another slot.

Order creation and its counter update share one transaction. Failed order
creation rolls back the counter update.

The source-aware order method is used by the HTTP controller. Existing
`submit()` methods remain available for trusted internal calls without
source-based limiting.

Rejected requests return HTTP 429 with `Retry-After` and `Cache-Control:
no-store`. Rejected requests do not extend the fixed window.

Login and public-form filters return HTTP 503 if counter storage is
unavailable. They do not bypass protection.

## Storage and cleanup

Counters survive backend restarts and are shared by instances using the
same database.

Bucket keys are SHA-256 hashes of the scope and identifier. They do not
store plaintext email addresses or IP addresses, but deterministic hashes
are not anonymization.

`RateLimitCleanup` runs hourly and removes counters that expired more than
24 hours earlier. Its interval is configured through
`lilimi.security.rate-limit.cleanup-delay-ms`.

## Deployment

The backend must not be directly accessible from the internet.

With forwarded-header processing enabled, source-address protection
depends on a correctly configured trusted gateway. The Caddy configuration
removes incoming `Forwarded` headers and relies on Caddy's default handling
of `X-Forwarded-*` headers.

The deployment workflow does not copy the server's Caddyfile automatically.

## Limitations

People sharing one public IP address share source-based limits.

IP limits alone do not stop distributed abuse. An attacker may temporarily
exhaust a known account's login budget.

CAPTCHA, administrator MFA and additional edge protection remain future
work.
