# Login rate limiting

Migration V27 adds persistent PostgreSQL buckets. All login attempts (successful
and failed) consume a slot before password verification. Defaults are 5 attempts
per normalized email and 30 attempts per source address in fixed 5-minute windows.
Nonexistent accounts use the same mechanism and responses. Counters survive
backend restarts and are shared by backend instances using the same database.
Rejected requests do not extend the window; there is no permanent account lock.
An attacker can still temporarily exhaust a known account's budget. CAPTCHA/MFA
and edge-level protection are future additional controls, not implemented here.

HTTP 429 includes Retry-After and a generic LOGIN_RATE_LIMITED response, without
disclosing whether the account exists or which bucket was exhausted. PostgreSQL
failures produce 503 instead of bypassing limits. CSRF validation runs first;
requests rejected by CSRF do not consume account buckets. Logout, browsing,
password-reset requests and authenticated sessions are not rate limited here.
Public-form abuse protection is a separate follow-up.

Settings: lilimi.security.login.account-attempts (5), source-attempts (30),
window-seconds (300, allowed 1..3600), cleanup-delay-ms (3600000). Expired buckets
older than 24 hours are removed hourly. SHA-256 bucket keys avoid plaintext
emails/IPs in this table, but are deterministic and are not anonymization.

## Deployment prerequisite: trusted proxy headers

The filter uses the servlet remote address, not raw forwarding headers. With
SERVER_FORWARD_HEADERS_STRATEGY=framework, this address comes from forwarded
headers. The reviewed Caddyfile explicitly removes Forwarded. Caddy supplies
X-Forwarded-For itself and, without trusted_proxies configured, ignores incoming
client-supplied X-Forwarded-* values. Apply these Caddyfile changes on
Lightsail and validate/reload the gateway before testing the source limit.
Backend port 8080 must not be exposed publicly. With another upstream proxy or
CDN, configure an explicit trusted proxy chain instead of trusting arbitrary
client headers. Without this setup source-IP limiting may be spoofed or may
aggregate clients behind a proxy; the independent account limit still applies.

The backend image deploy workflow does not copy Caddyfile from Git onto Lightsail.
Do not assume merging this change updates the server gateway configuration.

## Verification

Use automated tests against the isolated Testcontainers database; do not brute
force production. After deployment check a normal login/logout and recovery of
an exhausted test account after the window. Never log credentials, tokens or raw
email/source values when adding telemetry.

Proxy header reference: https://caddyserver.com/docs/caddyfile/directives/reverse_proxy#defaults
