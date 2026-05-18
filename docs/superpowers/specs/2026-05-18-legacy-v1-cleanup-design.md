# Legacy v1 Reference Cleanup — xml-signing-service

**Date:** 2026-05-18

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all stale v1-era code and fix config inconsistencies in xml-signing-service introduced during the CSC v2.0 wire format migration.

**Scope:** xml-signing-service only. Not pdf-signing-service or other services.

**Architecture:** No architectural changes — pure cleanup of stale config keys, dead fields, and inconsistent paths.

---

## Changes

### 1. Fix `CSCErrorDecoder` config path (Production Risk)

**File:** `src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CSCErrorDecoder.java:45`

Change `@Value("${app.csc.client-id}")` → `@Value("${app.csc.oauth2.client-id}")`

**Why:** The stale top-level key `${app.csc.client-id}` is null in non-prod profiles (only `oauth2.client-id` exists in main `application.yml`). This decoder already has `oauth2.client-id` in prod config — making it inconsistent between environments.

---

### 2. Restructure `application-prod.yml` (Production Risk)

**File:** `src/main/resources/application-prod.yml`

Move top-level `client-id:` under `oauth2:` block:

```yaml
# Before (v1 structure, stale)
app:
  csc:
    service-url: ${CSC_SERVICE_URL:http://localhost:9000}
    client-id: ${CSC_CLIENT_ID:etax-invoice-service}

# After (v2 structure)
app:
  csc:
    service-url: ${CSC_SERVICE_URL:http://localhost:9000}
    oauth2:
      client-id: ${CSC_CLIENT_ID:etax-invoice-service}
```

Remove any other top-level v1 keys (e.g., flat `client-secret:` if present).

---

### 3. Restructure `application-full-integration-test.yml`

**File:** `src/test/resources/application-full-integration-test.yml:73`

Change top-level `client-id: ${CSC_CLIENT_ID:dynamic}` → `oauth2.client-id: ${CSC_CLIENT_ID:dynamic}`

Keep other keys (`credential-id`, `pin`, `hash-algorithm-oid`, `digest-algorithm`) as-is.

---

### 4. Remove `clientId` from `CscAuthorizationException`

**File:** `src/main/java/com/wpanther/xmlsigning/domain/exception/CscAuthorizationException.java`

Remove:
- `clientId` field (line 20)
- `getClientId()` method
- Both constructors' `String clientId` parameters and javadoc `@param clientId`
- Update all `@param credentialId` javadoc to reflect current usage

**Call sites to update:**

- `CSCErrorDecoder.java:86-101` — remove `clientId` arguments when constructing `CscAuthorizationException`
- `XmlSigningServiceImpl.java` — remove `clientId` argument when constructing (already passing `null`)

---

### 5. Restructure test config files

**Files:**
- `src/test/resources/application-test.yml:57,60`
- `src/test/resources/application-consumer-test.yml:67-70`
- `src/test/resources/application-cdc-test.yml:53-56`

For each:

| Stale key | Replace with |
|-----------|-------------|
| `client-id:` (top-level) | `oauth2.client-id:` |
| `hash-algorithm: SHA256` | `hash-algorithm-oid: 2.16.840.1.101.3.4.2.1` |
| `signature-level: XAdES-BASELINE-T` | Remove (local XAdES config — determine if needed) |

Group under `oauth2:` sub-block where not already present.

---

## Files to Modify

| File | Change |
|------|--------|
| `src/main/java/.../config/feign/CSCErrorDecoder.java` | Fix `@Value` path; remove `clientId` from exception constructors |
| `src/main/java/.../exception/CscAuthorizationException.java` | Remove `clientId` field, getter, constructor params |
| `src/main/java/.../application/usecase/XmlSigningServiceImpl.java` | Remove `clientId` arg from exception construction |
| `src/main/resources/application-prod.yml` | Restructure `client-id` under `oauth2` |
| `src/test/resources/application-full-integration-test.yml` | Fix top-level `client-id` → `oauth2.client-id` |
| `src/test/resources/application-test.yml` | Restructure to v2 keys |
| `src/test/resources/application-consumer-test.yml` | Restructure to v2 keys |
| `src/test/resources/application-cdc-test.yml` | Restructure to v2 keys |

---

## Testing

- `mvn clean test` must pass with 0 failures
- No new compilation warnings
- No changes to runtime behavior — all changes are cosmetic/config normalization

---

## Not in Scope

- `pdf-signing-service` legacy cleanup
- Any new features or architectural changes
- Other services in the invoice-microservices repo