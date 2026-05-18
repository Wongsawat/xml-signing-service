# CSC v2.0 Wire Format Update — xml-signing-service

**Date**: 2026-05-18
**Service**: `xml-signing-service` (Spring Boot 3.2.5, Java 21)
**Trigger**: `eidasremotesigning` updated to CSC API v2.0.0.2 compliance (see `eidasremotesigning/docs/superpowers/specs/2026-05-18-csc-compliance-fix-design.md`)
**Approach**: Bottom-up layer-by-layer (Feign DTOs → adapters → domain records → application service). Add `CscCredentialInfoCache` for startup-fetched signing cert. Clean up stale CSC v1 domain concepts throughout.
**Reference**: Mirrors `pdf-signing-service/docs/superpowers/specs/2026-05-18-csc-v2-compliance-update-design.md` with xml-signing-specific adaptations.

## Scope

Update the CSC adapter layer to match the new wire format exposed by the updated `eidasremotesigning` service. No changes to XAdES construction (Apache Santuario), storage, Saga orchestration, or outbox pattern.

Breaking changes in eidasremotesigning that affect this service:
- `clientId` removed from all request bodies (JWT carries identity)
- `hashAlgo` → `hashAlgorithmOID` (OID string, not JCA name)
- `hash[]` → `hashes[]` (renamed array field)
- `numSignatures` type: `String` → `Integer`
- PIN delivery moved from `signHash.credentials.pin` → `authorize.authData[{id:"PIN",value:"..."}]`
- `authorize` response: `transactionID` removed
- `signHash` request: `signatureData` wrapper removed; `hashes[]` now flat at request root; `SignatureAttributes` removed
- `signHash` response: `certificate` and `timestampData` removed; `operationID` → `responseID`

---

## Section 1 — Wire Format (Feign DTO) Changes

### `CSCAuthorizeRequest`

| Field | Change |
|-------|--------|
| `clientId` | **Remove** |
| `hashAlgo: String` | Rename → `hashAlgorithmOID: String` |
| `hash: String[]` | Rename → `hashes: String[]` |
| `numSignatures: String` | Change type → `Integer` |
| `validityPeriod` | **Remove** (not in v2.0 spec) |
| `description` | Keep |
| *(new)* `authData: List<AuthDataEntry>` | **Add** — replaces `signHash.credentials.pin` |

**New static inner class `AuthDataEntry`:**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)
public static class AuthDataEntry {
    @JsonProperty("id")    private String id;    // e.g. "PIN"
    @JsonProperty("value") private String value;
}
```

### `CSCAuthorizeResponse`

| Field | Change |
|-------|--------|
| `transactionID` | **Remove** |
| `authMode` | **Remove** |
| `SAD`, `expiresIn` | Keep |

### `CSCSignatureRequest`

| Field | Change |
|-------|--------|
| `clientId` | **Remove** |
| `hashAlgo` | Rename → `hashAlgorithmOID` |
| `signatureData: SignatureData` | **Remove** wrapper entirely |
| *(new)* `hashes: String[]` | **Add** flat at root |
| `credentials: Credentials` | **Remove** (PIN moves to authorize) |
| `signatureOptions` | **Remove** |
| `credentialID`, `SAD` | Keep |

**Deleted inner classes**: `Credentials`, `Pin`, `SignatureOptions`.

### `CSCSignatureResponse`

| Field | Change |
|-------|--------|
| `certificate` | **Remove** (cert now from `CscCredentialInfoCache`) |
| `timestampData` | **Remove** |
| `operationID` | Rename → `responseID` |
| `signatures[]`, `signatureAlgorithm` | Keep |

### Deleted classes

- `SignatureData.java` — entire class deleted (was the `signatureData` wrapper)
- `SignatureAttributes.java` — entire class deleted (was XAdES metadata inside `signatureData`)

### New DTOs for `credentials/info`

**`CSCCredentialsInfoRequest`:**
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)
public class CSCCredentialsInfoRequest {
    @JsonProperty("credentialID") private String credentialID;
}
```

**`CSCCredentialsInfoResponse`** (minimal — only fields consumed):
```java
@Data @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)
public class CSCCredentialsInfoResponse {
    @JsonProperty("cert") private CertInfo cert;

    @Data @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)
    public static class CertInfo {
        @JsonProperty("certificates") private String[] certificates; // Base64-encoded DER
    }
}
```

---

## Section 2 — New Components

### New Feign client: `CSCCredentialsInfoClient`

**Location**: `infrastructure/client/csc/CSCCredentialsInfoClient.java`

```java
@FeignClient(
    name = "csc-credentials-info-client",
    url = "${app.csc.service-url}"
)
public interface CSCCredentialsInfoClient {
    @PostMapping("/csc/v2/credentials/info")
    CSCCredentialsInfoResponse getCredentialInfo(@RequestBody CSCCredentialsInfoRequest request);
}
```

Uses the default Feign config (same Bearer token auth as `CSCAuthClient`).

### New component: `CscCredentialInfoCache`

**Location**: `infrastructure/adapter/out/csc/CscCredentialInfoCache.java`

Fetches `credentials/info` on `@PostConstruct` (fail-fast if CSC unavailable at startup), caches `cert.certificates[0]` as a `volatile String`.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CscCredentialInfoCache {

    private final CSCCredentialsInfoClient credentialsInfoClient;

    @Value("${app.csc.credential-id}")
    private String credentialId;

    private volatile String certificate;

    @PostConstruct
    public void init() {
        refresh();
    }

    public String getCertificate() {
        return certificate;
    }

    public void refresh() {
        CSCCredentialsInfoResponse response = credentialsInfoClient.getCredentialInfo(
            new CSCCredentialsInfoRequest(credentialId)
        );
        String[] certs = response.getCert().getCertificates();
        if (certs == null || certs.length == 0) {
            throw new IllegalStateException("credentials/info returned empty certificate array for credentialID=" + credentialId);
        }
        certificate = certs[0];
        log.info("Cached signing certificate from credentials/info for credentialID={}", credentialId);
    }
}
```

**Why `String`?** `XadesSignatureEmbedder.parseCertificate(String base64Certificate)` already decodes Base64-DER via `CertificateFactory`. The `credentials/info` response delivers each cert in the same Base64-encoded DER format — no conversion needed. The `XadesEmbeddingPort` interface signature is unchanged.

If `@PostConstruct` throws, Spring context fails to start — acceptable since signing without a cert is impossible.

---

## Section 3 — Domain Record Cleanup

### `CscAuthorizeCommand`

**Before**: `(clientId, credentialId, numSignatures: String, hashAlgorithm, documentDigests, description)`

**After**: `(credentialId, hashAlgorithmOid, hashes: List<String>, pin, description)`

- `clientId` removed (OAuth2 JWT carries identity)
- `numSignatures` removed (hardcoded to `1` in `XmlSigningServiceImpl`)
- `hashAlgorithm` → `hashAlgorithmOid` (reflects actual OID value)
- `documentDigests` → `hashes`
- `pin` **added** — adapter builds `authData` from it when non-blank

### `CscAuthorizeResult`

**Before**: `(sadToken, transactionId)`

**After**: `(sadToken)` — `transactionId` removed; `authorize` response no longer carries `transactionID`

### `CscSignHashCommand`

**Before**: `(clientId, credentialId, sadToken, pin, hashAlgorithm, documentDigests, signatureType, signatureLevel, signatureForm, digestAlgorithm, signDate)`

**After**: `(credentialId, sadToken, hashAlgorithmOid, hashes: List<String>)`

- `clientId`, `pin` removed
- `signatureType`, `signatureLevel`, `signatureForm`, `digestAlgorithm`, `signDate` removed — these only existed to populate `SignatureAttributes` inside the now-deleted `signatureData` wrapper

### `CscSignHashResult`

**Before**: `(signatures: List<String>, certificate: String)`

**After**: `(signatures: List<String>, responseId: String)` — `certificate` removed (cert from `CscCredentialInfoCache`); `responseId` **added** carrying `CSCSignatureResponse.responseID` for audit traceability

### `SigningResult`

**Before**: `(signedXml, certificate, transactionId)` with `requireNonBlank` on `transactionId`

**After**: `(signedXml, certificate, responseId)` — field renamed to `responseId`, sourced from `signHash.responseID`. `requireNonBlank` guard kept on `responseId`.

---

## Section 4 — Adapter Updates

### `CscAuthorizationAdapter`

Mapping `CscAuthorizeCommand` → `CSCAuthorizeRequest`:

| Domain field | Wire field | Note |
|---|---|---|
| `credentialId` | `credentialID` | unchanged |
| `hashAlgorithmOid` | `hashAlgorithmOID` | renamed |
| `hashes` (List→array) | `hashes` | renamed |
| hardcoded `1` (Integer) | `numSignatures` | was `command.numSignatures()` String |
| `pin` (non-blank) | `authData: [{id:"PIN", value:pin}]` | moved from signHash |
| `description` | `description` | unchanged |

`authData` is omitted (null) when `pin` is null or blank — `@JsonInclude(NON_NULL)` handles serialization.

`validateResponse()`: remove `transactionID` null-check; keep only `SAD` null/blank check.

Mapping `CSCAuthorizeResponse` → `CscAuthorizeResult`:
- `response.getSAD()` → `sadToken` (single field now)

### `CscSignatureAdapter`

Mapping `CscSignHashCommand` → `CSCSignatureRequest`:

| Domain field | Wire field | Note |
|---|---|---|
| `credentialId` | `credentialID` | unchanged |
| `sadToken` | `SAD` | unchanged |
| `hashAlgorithmOid` | `hashAlgorithmOID` | renamed |
| `hashes` (List→array) | `hashes` | flat at root, no wrapper |

`SignatureData`, `SignatureAttributes`, and `Credentials` construction — all deleted.

`validateResponse()`: remove `getCertificate()` null-check; keep only `signatures` null/empty check.

Mapping `CSCSignatureResponse` → `CscSignHashResult`:
- `Arrays.asList(response.getSignatures())` → `signatures` (unchanged)
- `response.getResponseID()` → `responseId` (new field replacing removed `operationID`)
- `response.getCertificate()` call removed

---

## Section 5 — Application Service and Config

### `XmlSigningServiceImpl`

**New dependency**: `CscCredentialInfoCache credentialInfoCache` (injected via `@RequiredArgsConstructor`).

**Removed `@Value` fields**: `clientId` (`${app.csc.client-id}`), `signatureLevel` — these no longer flow into CSC requests.

**Kept `@Value` fields**: `digestAlgorithm` (`${app.csc.digest-algorithm:SHA-256}`) is kept — it is used locally by `calculateDigest()` for `MessageDigest.getInstance()`. It is no longer passed into CSC commands.

**Remaining `@Value` fields**: `credentialId`, `hashAlgorithmOid` (renamed property), `pin`.

**Updated `signXml()` flow**:
1. Get `certificate` from `credentialInfoCache.getCertificate()` — no network call
2. Compute `documentDigest` locally — unchanged
3. `authorize`: build `CscAuthorizeCommand(credentialId, hashAlgorithmOid, List.of(digest), pin, description)` → `CscAuthorizeResult(sadToken)`
4. `signHash`: build `CscSignHashCommand(credentialId, sadToken, hashAlgorithmOid, List.of(digest))` → `CscSignHashResult(signatures)`
5. Embed: `xadesEmbeddingPort.embedSignature(xmlBytes, rawSigBytes, digest, certificate, documentId)` — unchanged
6. Return `new SigningResult(signedXml, certificate, signHashResult.responseId())`

**Removed**: `SigningApiResponse` private record (it carried `transactionId` from authorize; `responseId` now comes directly from `CscSignHashResult`). Logging previously using `transactionId` switches to logging the document ID or `responseId`.

### Config — `application.yml`

```yaml
app:
  csc:
    service-url: ${CSC_SERVICE_URL:http://localhost:9000}
    credential-id: ${CSC_CREDENTIAL_ID:default-credential}
    hash-algorithm-oid: ${CSC_HASH_ALGORITHM_OID:2.16.840.1.101.3.4.2.1}
    pin: ${CSC_PIN:}
    # removed: client-id (still present under app.csc.oauth2.client-id for OAuth2 token only)
    # removed: hash-algorithm, signature-level, digest-algorithm
```

**`CscBearerTokenConfig`**: The `${app.csc.client-id}` value is for the OAuth2 `client_credentials` grant body — not the CSC request body. Rename the property to `app.csc.oauth2.client-id` to make the separation explicit and avoid confusion with the removed request-body `clientId`.

### `pom.xml` — testcontainers version bump

Align with `pdf-signing-service`: upgrade testcontainers from `1.19.7` → `2.0.5` for all three artifacts (`testcontainers`, `testcontainers-postgresql`, `testcontainers-kafka`). No other pom changes needed.

### `application.yml` — Feign client config

Add `csc-credentials-info-client` to `spring.cloud.openfeign.client.config`:
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          csc-credentials-info-client:
            url: ${CSC_SERVICE_URL:http://localhost:9000}
```

---

## Section 6 — Test Updates

### `CSCDtoTest`

Update all four DTO class assertions:
- **`CSCAuthorizeRequest`**: assert `hashAlgorithmOID`, `hashes`, `authData`, `numSignatures` as integer; assert absence of `clientId`, `hashAlgo`, `hash`, `validityPeriod`
- **`CSCAuthorizeResponse`**: assert `SAD`, `expiresIn`; assert absence of `transactionID`, `authMode`
- **`CSCSignatureRequest`**: assert flat `hashes` at root, `hashAlgorithmOID`; assert absence of `clientId`, `signatureData`, `credentials`, `hashAlgo`
- **`CSCSignatureResponse`**: assert `responseID`; assert absence of `operationID`, `certificate`, `timestampData`
- **Delete** `SignatureData` and `SignatureAttributes` test nested classes

### `CscDomainValueObjectsTest`

- Update `CscAuthorizeCommand` construction: remove `clientId`/`numSignatures`, add `hashAlgorithmOid`/`hashes`/`pin`
- Update `CscAuthorizeResult` construction: remove `transactionId`
- Update `CscSignHashCommand` construction: remove `clientId`/`pin`/`signatureType`/etc., add `hashAlgorithmOid`/`hashes`
- Update `CscSignHashResult` construction: remove `certificate`, add `responseId`

### `CscAuthorizationAdapterTest`

- Remove `transactionID` stub and assertion
- Assert `authData` present when pin non-blank; assert `authData` null when pin blank
- Assert `numSignatures` is `Integer` `1`
- Assert no `clientId` in built request

### `CscSignatureAdapterTest`

- Remove `certificate` from `CSCSignatureResponse` stub
- Assert no `signatureData`, no `credentials`, no `clientId` in built request
- Assert flat `hashes` present at root

### New: `CscCredentialInfoCacheTest`

- Happy path: `@PostConstruct` calls `credentialsInfoClient.getCredentialInfo()`, caches `certificates[0]`
- `getCertificate()` returns cached value without additional client calls
- `refresh()` updates the cached value
- Startup failure: client throws → exception propagates from `init()`
- Empty cert array → `IllegalStateException` from `refresh()`

### `XmlSigningServiceImplTest`

- Add `@Mock CscCredentialInfoCache mockCredentialInfoCache`
- Remove `clientId` stubs
- Mock `mockCredentialInfoCache.getCertificate()` to return a cert string
- Assert `authorize` command has `hashAlgorithmOid`, `hashes`, no `clientId`
- Assert `signHash` command has `hashAlgorithmOid`, `hashes`, no `pin`
- Assert `SigningResult.responseId()` comes from `signHash` response `responseID`
- Assert `parseCertificateChain` / cert extraction from signHash response is **not** called

---

## Cross-Cutting Notes

- **No Saga or outbox changes** — all Saga topology, Kafka topics, and outbox pattern remain unchanged
- **No XAdES construction changes** — `XadesSignatureEmbedder`, `XadesEmbeddingPort`, and the Apache Santuario signing pipeline are untouched
- **No storage changes** — MinIO and local storage backends unaffected
- **`CscBearerTokenConfig`** OAuth2 client credentials grant is unaffected; only the config key is renamed from `app.csc.client-id` to `app.csc.oauth2.client-id` for clarity

---

## File Change Summary

| File | Change type |
|------|-------------|
| `infrastructure/client/csc/dto/CSCAuthorizeRequest.java` | Modify |
| `infrastructure/client/csc/dto/CSCAuthorizeResponse.java` | Modify |
| `infrastructure/client/csc/dto/CSCSignatureRequest.java` | Modify |
| `infrastructure/client/csc/dto/CSCSignatureResponse.java` | Modify |
| `infrastructure/client/csc/dto/SignatureData.java` | **Delete** |
| `infrastructure/client/csc/dto/SignatureAttributes.java` | **Delete** |
| `infrastructure/client/csc/dto/CSCCredentialsInfoRequest.java` | **New** |
| `infrastructure/client/csc/dto/CSCCredentialsInfoResponse.java` | **New** |
| `infrastructure/client/csc/CSCCredentialsInfoClient.java` | **New** |
| `infrastructure/adapter/out/csc/CscCredentialInfoCache.java` | **New** |
| `infrastructure/adapter/out/csc/CscAuthorizationAdapter.java` | Modify |
| `infrastructure/adapter/out/csc/CscSignatureAdapter.java` | Modify |
| `application/dto/csc/CscAuthorizeCommand.java` | Modify |
| `application/dto/csc/CscAuthorizeResult.java` | Modify |
| `application/dto/csc/CscSignHashCommand.java` | Modify |
| `application/dto/csc/CscSignHashResult.java` | Modify |
| `application/usecase/SigningResult.java` | Modify |
| `application/usecase/XmlSigningServiceImpl.java` | Modify |
| `infrastructure/config/feign/CscBearerTokenConfig.java` | Modify (rename property key) |
| `src/main/resources/application.yml` | Modify |
| `pom.xml` | Modify (testcontainers `1.19.7` → `2.0.5`) |
| `test/.../dto/CSCDtoTest.java` | Modify |
| `test/.../CscDomainValueObjectsTest.java` | Modify |
| `test/.../CscAuthorizationAdapterTest.java` | Modify |
| `test/.../CscSignatureAdapterTest.java` | Modify |
| `test/.../CscCredentialInfoCacheTest.java` | **New** |
| `test/.../XmlSigningServiceImplTest.java` | Modify |

Base package: `com/wpanther/xmlsigning`
