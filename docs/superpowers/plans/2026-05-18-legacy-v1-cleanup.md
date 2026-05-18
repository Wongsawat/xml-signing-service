# Legacy v1 Reference Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove all stale v1-era code and fix config inconsistencies in xml-signing-service.

**Architecture:** No architectural changes — pure cleanup of stale config keys, dead fields, and inconsistent paths. All changes are cosmetic/config normalization with no runtime behavior change.

**Tech Stack:** Java 21, Spring Boot 3.2.5, JUnit 5 + AssertJ + Mockito

---

### Task 1: Fix `CSCErrorDecoder` config path

**Files:**
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CSCErrorDecoder.java:45`

- [ ] **Step 1: Change `@Value` path from `${app.csc.client-id}` to `${app.csc.oauth2.client-id}`**

In the constructor at line 45:
```java
// Before
@Value("${app.csc.client-id}") String clientId,

// After
@Value("${app.csc.oauth2.client-id}") String clientId,
```

Also update the `log.debug` at line 49 to remove `clientId` from the log message since it will now read from the correct path.

- [ ] **Step 2: Run tests to verify no breakage**

Run: `mvn clean test -Dtest=CscErrorDecoderTest 2>&1 | tail -20`
Expected: PASS (no existing test, but verify no compile errors)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CSCErrorDecoder.java
git commit -m "fix: update CSCErrorDecoder to read oauth2.client-id instead of stale top-level client-id"
```

---

### Task 2: Restructure `application-prod.yml` — move `client-id` under `oauth2`

**Files:**
- Modify: `src/main/resources/application-prod.yml:75-81`

- [ ] **Step 1: Restructure the `csc:` block**

Current (lines 75-81):
```yaml
  csc:
    service-url: ${CSC_SERVICE_URL:http://localhost:9000}
    client-id: ${CSC_CLIENT_ID:etax-invoice-service}
    credential-id: ${CSC_CREDENTIAL_ID:default-credential}
    hash-algorithm: ${CSC_HASH_ALGORITHM:SHA256}
    signature-level: ${CSC_SIGNATURE_LEVEL:XAdES-BASELINE-T}
    digest-algorithm: ${CSC_DIGEST_ALGORITHM:SHA256}
```

Replace with:
```yaml
  csc:
    service-url: ${CSC_SERVICE_URL:http://localhost:9000}
    oauth2:
      client-id: ${CSC_CLIENT_ID:etax-invoice-service}
      client-secret: ${CSC_CLIENT_SECRET:}
    credential-id: ${CSC_CREDENTIAL_ID:default-credential}
    hash-algorithm-oid: ${CSC_HASH_ALGORITHM_OID:2.16.840.1.101.3.4.2.1}
    digest-algorithm: ${CSC_DIGEST_ALGORITHM:SHA-256}
```

Note: `signature-level` removed (not used in v2 CSC API flow — signature level is determined by the credential on the server side, not passed from the client). `hash-algorithm` renamed to `hash-algorithm-oid` to match v2 wire format. `client-secret` added under `oauth2`.

- [ ] **Step 2: Verify YAML is valid**

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=prod 2>&1 | head -5` (just check it starts parsing)
Expected: Spring Boot banner or error indicating YAML loaded (not tested in unit tests — this is a YAML restructure)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/application-prod.yml
git commit -m "fix: restructure application-prod.yml CSC config to v2 wire format"
```

---

### Task 3: Remove `clientId` from `CscAuthorizationException`

**Files:**
- Modify: `src/main/java/com/wpanther/xmlsigning/domain/exception/CscAuthorizationException.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CSCErrorDecoder.java:86-101`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImpl.java` (find where it constructs `CscAuthorizationException`)

- [ ] **Step 1: Rewrite `CscAuthorizationException` without `clientId`**

Remove the `clientId` field and getter. Simplify both constructors to only take `(message, credentialId)` and `(message, cause, credentialId)`. Update javadoc accordingly.

```java
// New CscAuthorizationException.java
package com.wpanther.xmlsigning.domain.exception;

public class CscAuthorizationException extends XmlSigningException {

    private final String credentialId;

    public CscAuthorizationException(String message, String credentialId) {
        super(message);
        this.credentialId = credentialId;
    }

    public CscAuthorizationException(String message, Throwable cause, String credentialId) {
        super(message, cause);
        this.credentialId = credentialId;
    }

    public String getCredentialId() {
        return credentialId;
    }
}
```

- [ ] **Step 2: Update `CSCErrorDecoder` — remove `clientId` from all `CscAuthorizationException` constructors**

In `decodeAuthorizationError` at lines 84-107, remove the `clientId` argument from each `new CscAuthorizationException(...)` call. Only pass `message` and `credentialId`.

Example:
```java
// Before
case 401 -> new CscAuthorizationException(
        "CSC authorization failed: Invalid client ID or credential ID.",
        clientId, credentialId);

// After
case 401 -> new CscAuthorizationException(
        "CSC authorization failed: Invalid client ID or credential ID.",
        credentialId);
```

- [ ] **Step 3: Verify no other call sites to `CscAuthorizationException`**

Run: `grep -rn "new CscAuthorizationException" src/`
Expected: Only occurrences are in `CSCErrorDecoder` (already fixed) and `XmlSigningServiceImpl`

Run: `grep -rn "CscAuthorizationException" src/` to check imports

- [ ] **Step 4: Update `XmlSigningServiceImpl` — find where it constructs `CscAuthorizationException` and remove `clientId` arg**

Run: `grep -n "CscAuthorizationException" src/main/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImpl.java`
Expected: One line constructing `CscAuthorizationException` — remove `clientId` arg (was already passing `null` or empty string)

- [ ] **Step 5: Run tests**

Run: `mvn clean test -Dtest=CscAuthorizationExceptionTest 2>&1 | tail -10`
If no specific test exists: `mvn clean test 2>&1 | grep -E "(Tests run|BUILD)" | tail -5`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/wpanther/xmlsigning/domain/exception/CscAuthorizationException.java \
       src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CSCErrorDecoder.java \
       src/main/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImpl.java
git commit -m "refactor: remove clientId from CscAuthorizationException (v1 remnant)"
```

---

### Task 4: Restructure test config files to v2 keys

**Files:**
- Modify: `src/test/resources/application-test.yml:55-62`
- Modify: `src/test/resources/application-consumer-test.yml:51-57`
- Modify: `src/test/resources/application-cdc-test.yml:65-71`

- [ ] **Step 1: Restructure `application-test.yml`**

Current (lines 55-62):
```yaml
  csc:
    service-url: http://localhost:19000
    client-id: test-client
    credential-id: test-credential
    pin: test-pin-1234
    hash-algorithm: SHA256
    signature-level: XAdES-BASELINE-T
    digest-algorithm: SHA256
```

Replace with:
```yaml
  csc:
    service-url: http://localhost:19000
    oauth2:
      client-id: test-client
    credential-id: test-credential
    pin: test-pin-1234
    hash-algorithm-oid: 2.16.840.1.101.3.4.2.1
    digest-algorithm: SHA-256
```

Note: `signature-level` removed (local XAdES config not passed to CSC API). `hash-algorithm` → `hash-algorithm-oid`. `client-id` moved under `oauth2`.

- [ ] **Step 2: Restructure `application-consumer-test.yml`**

Current (lines 51-57):
```yaml
  csc:
    service-url: http://localhost:19000
    client-id: test-client
    credential-id: test-credential
    hash-algorithm: SHA256
    signature-level: XAdES-BASELINE-T
    digest-algorithm: SHA256
```

Replace with:
```yaml
  csc:
    service-url: http://localhost:19000
    oauth2:
      client-id: test-client
    credential-id: test-credential
    hash-algorithm-oid: 2.16.840.1.101.3.4.2.1
    digest-algorithm: SHA-256
```

- [ ] **Step 3: Restructure `application-cdc-test.yml`**

Current (lines 65-71):
```yaml
  csc:
    service-url: http://localhost:19000
    client-id: test-client
    credential-id: test-credential
    hash-algorithm: SHA256
    signature-level: XAdES-BASELINE-T
    digest-algorithm: SHA256
```

Replace with same pattern as other test configs.

- [ ] **Step 4: Run tests**

Run: `mvn clean test 2>&1 | grep -E "(Tests run|BUILD)" | tail -5`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/application-test.yml \
       src/test/resources/application-consumer-test.yml \
       src/test/resources/application-cdc-test.yml
git commit -m "fix: update test configs to CSC v2 wire format (oauth2.client-id, hash-algorithm-oid)"
```

---

### Task 5: Final verification

- [ ] **Step 1: Run full test suite**

Run: `mvn clean test 2>&1 | tail -10`
Expected: `BUILD SUCCESS`, all tests pass with 0 failures

- [ ] **Step 2: Verify no stale references remain**

Run: `grep -rn "app\.csc\.client-id" src/` — should return no results in src/main/
Run: `grep -rn "hash-algorithm:" src/test/resources/` — should return no results (only `hash-algorithm-oid`)
Run: `grep -rn "signature-level:" src/test/resources/` — should return no results

- [ ] **Step 3: Final push**

```bash
git push
```