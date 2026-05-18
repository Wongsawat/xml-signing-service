# CSC v2.0 Wire Format Update — xml-signing-service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the xml-signing-service CSC adapter layer to match the wire format changes in eidasremotesigning CSC API v2.0.0.2 compliance, removing stale v1 concepts throughout the hexagonal architecture.

**Architecture:** Bottom-up layer-by-layer: Feign DTOs → new credentials/info components → domain records → adapters → application service. `CscCredentialInfoCache` fetches the signing certificate at startup from `credentials/info` and caches it as a `String`, which is the format `XadesSignatureEmbedder.parseCertificate()` already accepts — no format conversion needed. Each task leaves the codebase compiling except where noted.

**Tech Stack:** Java 21, Spring Boot 3.2.5, OpenFeign, Lombok, Jackson, JUnit 5 + AssertJ + Mockito

**Spec:** `docs/superpowers/specs/2026-05-18-csc-v2-compliance-update-design.md`

---

## File Map

| File | Action |
|------|--------|
| `pom.xml` | Modify (testcontainers `1.19.7` → `2.0.5`) |
| `src/main/resources/application.yml` | Modify (rename CSC props, add feign client) |
| `src/test/resources/application-full-integration-test.yml` | Modify (rename CSC props) |
| `infrastructure/config/feign/CscBearerTokenConfig.java` | Modify (`@Value` key rename) |
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
| `application/dto/csc/CscAuthorizeCommand.java` | Modify |
| `application/dto/csc/CscAuthorizeResult.java` | Modify |
| `application/dto/csc/CscSignHashCommand.java` | Modify |
| `application/dto/csc/CscSignHashResult.java` | Modify |
| `application/usecase/SigningResult.java` | Modify |
| `infrastructure/adapter/out/csc/CscAuthorizationAdapter.java` | Modify |
| `infrastructure/adapter/out/csc/CscSignatureAdapter.java` | Modify |
| `application/usecase/XmlSigningServiceImpl.java` | Modify |
| `application/usecase/SagaCommandHandler.java` | Modify (1-line fix) |
| `test/.../dto/CSCDtoTest.java` | Modify |
| `test/.../CscDomainValueObjectsTest.java` | Modify |
| `test/.../CscAuthorizationAdapterTest.java` | Modify |
| `test/.../CscSignatureAdapterTest.java` | Modify |
| `test/.../CscCredentialInfoCacheTest.java` | **New** |
| `test/.../XmlSigningServiceImplTest.java` | Modify |

Base package: `com/wpanther/xmlsigning`

---

## Task 1: pom.xml — Testcontainers Version Bump

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Update testcontainers version**

  In `pom.xml`, find the three testcontainers artifacts (all at `1.19.7`) and change each version to `2.0.5`:

  ```xml
  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers</artifactId>
      <version>2.0.5</version>
      <scope>test</scope>
  </dependency>

  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-postgresql</artifactId>
      <version>2.0.5</version>
      <scope>test</scope>
  </dependency>

  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-kafka</artifactId>
      <version>2.0.5</version>
      <scope>test</scope>
  </dependency>
  ```

- [ ] **Step 2: Verify dependency resolves**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn dependency:resolve -q 2>&1 | grep -i "testcontainers\|ERROR" | head -10
  ```

  Expected: No ERROR output. Testcontainers 2.0.5 resolved.

- [ ] **Step 3: Commit**

  ```bash
  git add pom.xml
  git commit -m "build(xml-signing): bump testcontainers 1.19.7 → 2.0.5"
  ```

---

## Task 2: Config — application.yml, Integration Test yml, CscBearerTokenConfig

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application-full-integration-test.yml`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CscBearerTokenConfig.java`

- [ ] **Step 1: Update `application.yml` — replace the `app.csc` block**

  Find and replace the entire `app.csc` block (lines starting with `csc:` through `digest-algorithm: ...`):

  ```yaml
    csc:
      service-url: ${CSC_SERVICE_URL:http://localhost:9000}
      oauth2:
        client-id: ${CSC_CLIENT_ID:etax-invoice-service}
        client-secret: ${CSC_CLIENT_SECRET:}
      credential-id: ${CSC_CREDENTIAL_ID:default-credential}
      pin: ${CSC_PIN:}
      hash-algorithm-oid: ${CSC_HASH_ALGORITHM_OID:2.16.840.1.101.3.4.2.1}
      digest-algorithm: ${CSC_DIGEST_ALGORITHM:SHA-256}
  ```

  Removed: `client-id` at top level, `signature-level`
  Renamed: `hash-algorithm` → `hash-algorithm-oid` (value is now an OID not a JCA name)
  Added: `oauth2.client-id`, `oauth2.client-secret` (for `CscBearerTokenConfig` OAuth2 grant only)

- [ ] **Step 2: Add `csc-credentials-info-client` to `spring.cloud.openfeign.client.config`**

  In `application.yml`, find the `spring.cloud.openfeign.client.config` block and add the new client entry after `default:`:

  ```yaml
    cloud:
      openfeign:
        client:
          config:
            default:
              connectTimeout: 10000
              readTimeout: 30000
              loggerLevel: FULL
            csc-credentials-info-client:
              connectTimeout: 10000
              readTimeout: 30000
  ```

- [ ] **Step 3: Update `application-full-integration-test.yml`**

  Find and replace the `app.csc` section in this file:

  ```yaml
    csc:
      service-url: ${CSC_SERVICE_URL:http://localhost:9000}
      oauth2:
        client-id: ${CSC_CLIENT_ID:dynamic}
        client-secret: ${CSC_CLIENT_SECRET:dynamic}
      credential-id: ${CSC_CREDENTIAL_ID:default-credential}
      pin: ${CSC_PIN:}
      hash-algorithm-oid: 2.16.840.1.101.3.4.2.1
      digest-algorithm: SHA-256
  ```

  Also add `csc-credentials-info-client` to the `resilience4j.circuitbreaker.instances` and `resilience4j.timelimiter.instances` sections (copy the same config as `cscSignatureClient`).

- [ ] **Step 4: Update `CscBearerTokenConfig.java`**

  Change the two `@Value` annotations to use the renamed property paths:

  ```java
  @Value("${app.csc.oauth2.client-id}")
  private String clientId;

  @Value("${app.csc.oauth2.client-secret}")
  private String clientSecret;
  ```

  All other code in `CscBearerTokenConfig` is unchanged.

- [ ] **Step 5: Verify compilation**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn compile -q
  ```

  Expected: BUILD SUCCESS. (`XmlSigningServiceImpl` still reads `${app.csc.client-id}` which no longer exists in yml — this will be a runtime binding failure, but compile succeeds. It is fixed in Task 9.)

- [ ] **Step 6: Commit**

  ```bash
  git add src/main/resources/application.yml \
          src/test/resources/application-full-integration-test.yml \
          src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/CscBearerTokenConfig.java
  git commit -m "refactor(xml-signing): rename CSC config properties for v2.0 compliance"
  ```

---

## Task 3: Feign DTOs + CSCDtoTest (TDD)

**Files:**
- Modify: `src/test/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCDtoTest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCAuthorizeRequest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCAuthorizeResponse.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCSignatureRequest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCSignatureResponse.java`
- Delete: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/SignatureData.java`
- Delete: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/SignatureAttributes.java`

- [ ] **Step 1: Replace `CSCDtoTest.java` with updated assertions**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import org.junit.jupiter.api.DisplayName;
  import org.junit.jupiter.api.Nested;
  import org.junit.jupiter.api.Test;

  import java.util.List;

  import static org.assertj.core.api.Assertions.assertThat;

  @DisplayName("CSC DTO Tests")
  class CSCDtoTest {

      private final ObjectMapper objectMapper = new ObjectMapper();

      @Nested
      @DisplayName("CSCAuthorizeRequest")
      class CSCAuthorizeRequestTests {

          @Test
          @DisplayName("Should serialize hashAlgorithmOID and hashes, no clientId/hashAlgo/hash")
          void shouldSerializeToJson() throws Exception {
              CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                  .credentialID("credential-123")
                  .numSignatures(1)
                  .hashAlgorithmOID("2.16.840.1.101.3.4.2.1")
                  .hashes(new String[]{"dGhpc2lzYXhash"})
                  .description("Test authorization")
                  .build();

              String json = objectMapper.writeValueAsString(request);

              assertThat(json).contains("\"credentialID\":\"credential-123\"");
              assertThat(json).contains("\"hashAlgorithmOID\":\"2.16.840.1.101.3.4.2.1\"");
              assertThat(json).contains("\"hashes\"");
              assertThat(json).doesNotContain("\"clientId\"");
              assertThat(json).doesNotContain("\"hashAlgo\"");
              assertThat(json).doesNotContain("\"hash\":");
              assertThat(json).doesNotContain("\"validityPeriod\"");
          }

          @Test
          @DisplayName("numSignatures should serialize as JSON integer not string")
          void shouldSerializeNumSignaturesAsInteger() throws Exception {
              CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                  .credentialID("cred")
                  .numSignatures(1)
                  .build();

              String json = objectMapper.writeValueAsString(request);

              assertThat(json).contains("\"numSignatures\":1");
              assertThat(json).doesNotContain("\"numSignatures\":\"1\"");
          }

          @Test
          @DisplayName("authData should serialize as array of id/value objects")
          void shouldSerializeAuthData() throws Exception {
              CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                  .credentialID("cred")
                  .authData(List.of(
                      CSCAuthorizeRequest.AuthDataEntry.builder().id("PIN").value("1234").build()
                  ))
                  .build();

              String json = objectMapper.writeValueAsString(request);

              assertThat(json).contains("\"authData\"");
              assertThat(json).contains("\"id\":\"PIN\"");
              assertThat(json).contains("\"value\":\"1234\"");
          }

          @Test
          @DisplayName("Should exclude null fields from JSON")
          void shouldExcludeNullFields() throws Exception {
              CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                  .credentialID("cred")
                  .build();

              String json = objectMapper.writeValueAsString(request);

              assertThat(json).doesNotContain("numSignatures");
              assertThat(json).doesNotContain("hashAlgorithmOID");
              assertThat(json).doesNotContain("hashes");
              assertThat(json).doesNotContain("authData");
          }
      }

      @Nested
      @DisplayName("CSCAuthorizeResponse")
      class CSCAuthorizeResponseTests {

          @Test
          @DisplayName("Should serialize and deserialize SAD token")
          void shouldSerializeSADToken() throws Exception {
              CSCAuthorizeResponse response = new CSCAuthorizeResponse();
              response.setSAD("test-sad-token");
              response.setExpiresIn(300L);

              String json = objectMapper.writeValueAsString(response);
              CSCAuthorizeResponse deserialized = objectMapper.readValue(json, CSCAuthorizeResponse.class);

              assertThat(deserialized.getSAD()).isEqualTo("test-sad-token");
              assertThat(deserialized.getExpiresIn()).isEqualTo(300L);
          }

          @Test
          @DisplayName("Should not contain transactionID or authMode fields")
          void shouldNotContainRemovedFields() throws Exception {
              CSCAuthorizeResponse response = new CSCAuthorizeResponse();
              response.setSAD("token");

              String json = objectMapper.writeValueAsString(response);

              assertThat(json).doesNotContain("transactionID");
              assertThat(json).doesNotContain("authMode");
          }
      }

      @Nested
      @DisplayName("CSCSignatureRequest")
      class CSCSignatureRequestTests {

          @Test
          @DisplayName("Should serialize flat hashes array and hashAlgorithmOID at top level")
          void shouldSerializeFlatHashesAtTopLevel() throws Exception {
              CSCSignatureRequest request = CSCSignatureRequest.builder()
                  .credentialID("cred")
                  .SAD("sad-token")
                  .hashAlgorithmOID("2.16.840.1.101.3.4.2.1")
                  .hashes(new String[]{"base64hash"})
                  .build();

              String json = objectMapper.writeValueAsString(request);

              assertThat(json).contains("\"hashes\"");
              assertThat(json).contains("\"hashAlgorithmOID\":\"2.16.840.1.101.3.4.2.1\"");
              assertThat(json).contains("base64hash");
              assertThat(json).doesNotContain("\"clientId\"");
              assertThat(json).doesNotContain("\"signatureData\"");
              assertThat(json).doesNotContain("\"credentials\"");
              assertThat(json).doesNotContain("\"hashAlgo\"");
          }

          @Test
          @DisplayName("Should deserialize with hashes and hashAlgorithmOID")
          void shouldDeserializeCorrectly() throws Exception {
              String json = "{\"credentialID\":\"cr\",\"SAD\":\"st\",\"hashAlgorithmOID\":\"2.16.840.1.101.3.4.2.1\",\"hashes\":[\"hash1\"]}";

              CSCSignatureRequest request = objectMapper.readValue(json, CSCSignatureRequest.class);

              assertThat(request.getCredentialID()).isEqualTo("cr");
              assertThat(request.getHashes()).containsExactly("hash1");
              assertThat(request.getHashAlgorithmOID()).isEqualTo("2.16.840.1.101.3.4.2.1");
          }
      }

      @Nested
      @DisplayName("CSCSignatureResponse")
      class CSCSignatureResponseTests {

          @Test
          @DisplayName("Should serialize and deserialize signature array")
          void shouldSerializeSignatureArray() throws Exception {
              CSCSignatureResponse response = CSCSignatureResponse.builder()
                  .signatureAlgorithm("1.2.840.113549.1.1.11")
                  .signatures(new String[]{"c2lnbmF0dXJlZmxvYg==", "c2lnMj"})
                  .build();

              String json = objectMapper.writeValueAsString(response);
              CSCSignatureResponse deserialized = objectMapper.readValue(json, CSCSignatureResponse.class);

              assertThat(deserialized.getSignatures()).hasSize(2);
          }

          @Test
          @DisplayName("Should use responseID not operationID")
          void shouldUseResponseId() throws Exception {
              CSCSignatureResponse response = CSCSignatureResponse.builder()
                  .signatures(new String[]{"sig"})
                  .responseID("async-resp-123")
                  .build();

              String json = objectMapper.writeValueAsString(response);

              assertThat(json).contains("\"responseID\":\"async-resp-123\"");
              assertThat(json).doesNotContain("operationID");
          }

          @Test
          @DisplayName("Should not contain certificate or timestampData fields")
          void shouldNotContainRemovedFields() throws Exception {
              CSCSignatureResponse response = CSCSignatureResponse.builder()
                  .signatures(new String[]{"sig"})
                  .build();

              String json = objectMapper.writeValueAsString(response);

              assertThat(json).doesNotContain("certificate");
              assertThat(json).doesNotContain("timestampData");
          }
      }
  }
  ```

- [ ] **Step 2: Run test — expect compilation failures**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CSCDtoTest 2>&1 | grep -E "ERROR|cannot find|symbol"
  ```

  Expected: compilation errors — `hashAlgorithmOID`, `hashes`, `AuthDataEntry`, `responseID` fields do not yet exist.

- [ ] **Step 3: Replace `CSCAuthorizeRequest.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  import java.util.List;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class CSCAuthorizeRequest {

      @JsonProperty("credentialID")
      private String credentialID;

      @JsonProperty("numSignatures")
      private Integer numSignatures;

      @JsonProperty("hashAlgorithmOID")
      private String hashAlgorithmOID;

      @JsonProperty("hashes")
      private String[] hashes;

      @JsonProperty("authData")
      private List<AuthDataEntry> authData;

      @JsonProperty("description")
      private String description;

      @Data
      @Builder
      @NoArgsConstructor
      @AllArgsConstructor
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public static class AuthDataEntry {
          @JsonProperty("id")
          private String id;

          @JsonProperty("value")
          private String value;
      }
  }
  ```

- [ ] **Step 4: Replace `CSCAuthorizeResponse.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class CSCAuthorizeResponse {

      @JsonProperty("SAD")
      private String SAD;

      @JsonProperty("expiresIn")
      private Long expiresIn;
  }
  ```

- [ ] **Step 5: Replace `CSCSignatureRequest.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class CSCSignatureRequest {

      @JsonProperty("credentialID")
      private String credentialID;

      @JsonProperty("SAD")
      private String SAD;

      @JsonProperty("hashAlgorithmOID")
      private String hashAlgorithmOID;

      @JsonProperty("hashes")
      private String[] hashes;
  }
  ```

- [ ] **Step 6: Replace `CSCSignatureResponse.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class CSCSignatureResponse {

      @JsonProperty("signatureAlgorithm")
      private String signatureAlgorithm;

      @JsonProperty("signatures")
      private String[] signatures;

      @JsonProperty("responseID")
      private String responseID;
  }
  ```

- [ ] **Step 7: Delete `SignatureData.java`**

  ```bash
  rm src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/SignatureData.java
  ```

- [ ] **Step 8: Delete `SignatureAttributes.java`**

  ```bash
  rm src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/SignatureAttributes.java
  ```

- [ ] **Step 9: Run `CSCDtoTest` — expect all green**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CSCDtoTest
  ```

  Expected: BUILD SUCCESS, all tests PASS.

- [ ] **Step 10: Compile check — existing adapter and service errors are expected**

  ```bash
  mvn compile 2>&1 | grep -E "ERROR|error:" | grep -v "CscAuthorizationAdapter\|CscSignatureAdapter\|XmlSigningServiceImpl"
  ```

  Expected: No output (all compile errors are in adapters/service which are fixed in later tasks).

- [ ] **Step 11: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/ \
          src/test/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCDtoTest.java
  git commit -m "refactor(xml-signing): update CSC Feign DTOs for v2.0 wire format"
  ```

---

## Task 4: credentials/info DTOs and Feign Client

No TDD needed — these are new types with no independent behavior. The `CscCredentialInfoCacheTest` in Task 5 covers them through integration.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCCredentialsInfoRequest.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCCredentialsInfoResponse.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/CSCCredentialsInfoClient.java`

- [ ] **Step 1: Create `CSCCredentialsInfoRequest.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import lombok.AllArgsConstructor;
  import lombok.Builder;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class CSCCredentialsInfoRequest {

      @JsonProperty("credentialID")
      private String credentialID;
  }
  ```

- [ ] **Step 2: Create `CSCCredentialsInfoResponse.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

  import com.fasterxml.jackson.annotation.JsonInclude;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import lombok.AllArgsConstructor;
  import lombok.Data;
  import lombok.NoArgsConstructor;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public class CSCCredentialsInfoResponse {

      @JsonProperty("cert")
      private CertInfo cert;

      @Data
      @NoArgsConstructor
      @AllArgsConstructor
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public static class CertInfo {
          @JsonProperty("certificates")
          private String[] certificates;
      }
  }
  ```

- [ ] **Step 3: Create `CSCCredentialsInfoClient.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.client.csc;

  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoResponse;
  import org.springframework.cloud.openfeign.FeignClient;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.RequestBody;

  @FeignClient(
      name = "csc-credentials-info-client",
      url = "${app.csc.service-url}"
  )
  public interface CSCCredentialsInfoClient {

      @PostMapping("/csc/v2/credentials/info")
      CSCCredentialsInfoResponse getCredentialInfo(@RequestBody CSCCredentialsInfoRequest request);
  }
  ```

- [ ] **Step 4: Verify compilation**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn compile -q 2>&1 | grep -v "CscAuthorizationAdapter\|CscSignatureAdapter\|XmlSigningServiceImpl"
  ```

  Expected: No output other than known adapter/service errors from Task 3.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCCredentialsInfoRequest.java \
          src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCCredentialsInfoResponse.java \
          src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/CSCCredentialsInfoClient.java
  git commit -m "feat(xml-signing): add credentials/info DTOs and Feign client for CSC v2.0"
  ```

---

## Task 5: Add `CscCredentialInfoCache` (TDD)

**Files:**
- Create: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscCredentialInfoCacheTest.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscCredentialInfoCache.java`

- [ ] **Step 1: Create `CscCredentialInfoCacheTest.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

  import com.wpanther.xmlsigning.infrastructure.client.csc.CSCCredentialsInfoClient;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoResponse;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.DisplayName;
  import org.junit.jupiter.api.Nested;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.ArgumentCaptor;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import org.springframework.test.util.ReflectionTestUtils;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  @DisplayName("CscCredentialInfoCache Tests")
  class CscCredentialInfoCacheTest {

      @Mock
      private CSCCredentialsInfoClient mockClient;

      private CscCredentialInfoCache cache;

      @BeforeEach
      void setUp() {
          cache = new CscCredentialInfoCache(mockClient);
          ReflectionTestUtils.setField(cache, "credentialId", "test-cred-id");
      }

      @Nested
      @DisplayName("refresh()")
      class RefreshMethod {

          @Test
          @DisplayName("Should call credentials/info with configured credentialID and cache first cert")
          void shouldFetchAndCacheFirstCert() {
              when(mockClient.getCredentialInfo(any())).thenReturn(buildResponse("cert-base64"));

              cache.refresh();

              ArgumentCaptor<CSCCredentialsInfoRequest> captor =
                  ArgumentCaptor.forClass(CSCCredentialsInfoRequest.class);
              verify(mockClient).getCredentialInfo(captor.capture());
              assertThat(captor.getValue().getCredentialID()).isEqualTo("test-cred-id");
              assertThat(cache.getCertificate()).isEqualTo("cert-base64");
          }

          @Test
          @DisplayName("getCertificate() returns cached value without additional client calls")
          void shouldNotCallClientOnSubsequentGet() {
              when(mockClient.getCredentialInfo(any())).thenReturn(buildResponse("cert"));

              cache.refresh();
              cache.getCertificate();
              cache.getCertificate();

              verify(mockClient, times(1)).getCredentialInfo(any());
          }

          @Test
          @DisplayName("refresh() again replaces the cached certificate")
          void shouldUpdateCacheOnSecondRefresh() {
              when(mockClient.getCredentialInfo(any()))
                  .thenReturn(buildResponse("cert-first"))
                  .thenReturn(buildResponse("cert-second"));

              cache.refresh();
              assertThat(cache.getCertificate()).isEqualTo("cert-first");

              cache.refresh();
              assertThat(cache.getCertificate()).isEqualTo("cert-second");
          }

          @Test
          @DisplayName("Should throw IllegalStateException when certificate array is empty")
          void shouldThrowForEmptyCertArray() {
              CSCCredentialsInfoResponse.CertInfo certInfo =
                  new CSCCredentialsInfoResponse.CertInfo(new String[0]);
              when(mockClient.getCredentialInfo(any()))
                  .thenReturn(new CSCCredentialsInfoResponse(certInfo));

              assertThatThrownBy(() -> cache.refresh())
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("empty");
          }

          @Test
          @DisplayName("Should propagate exception when client throws")
          void shouldPropagateClientException() {
              when(mockClient.getCredentialInfo(any()))
                  .thenThrow(new RuntimeException("CSC unavailable"));

              assertThatThrownBy(() -> cache.refresh())
                  .isInstanceOf(RuntimeException.class)
                  .hasMessageContaining("CSC unavailable");
          }
      }

      private CSCCredentialsInfoResponse buildResponse(String base64DerCert) {
          CSCCredentialsInfoResponse.CertInfo certInfo =
              new CSCCredentialsInfoResponse.CertInfo(new String[]{base64DerCert});
          return new CSCCredentialsInfoResponse(certInfo);
      }
  }
  ```

- [ ] **Step 2: Run test — expect compilation failure**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscCredentialInfoCacheTest 2>&1 | grep -E "ERROR|cannot find"
  ```

  Expected: compilation error — `CscCredentialInfoCache` does not exist yet.

- [ ] **Step 3: Create `CscCredentialInfoCache.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

  import com.wpanther.xmlsigning.infrastructure.client.csc.CSCCredentialsInfoClient;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCCredentialsInfoResponse;
  import jakarta.annotation.PostConstruct;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.stereotype.Component;

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
          log.info("Fetching signing certificate from credentials/info for credentialID={}", credentialId);
          CSCCredentialsInfoResponse response = credentialsInfoClient.getCredentialInfo(
              new CSCCredentialsInfoRequest(credentialId)
          );
          String[] certs = response.getCert().getCertificates();
          if (certs == null || certs.length == 0) {
              throw new IllegalStateException(
                  "credentials/info returned empty certificate array for credentialID=" + credentialId);
          }
          certificate = certs[0];
          log.info("Cached signing certificate from credentials/info for credentialID={}", credentialId);
      }
  }
  ```

- [ ] **Step 4: Run `CscCredentialInfoCacheTest` — expect all green**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscCredentialInfoCacheTest
  ```

  Expected: BUILD SUCCESS, 5 tests PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscCredentialInfoCache.java \
          src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscCredentialInfoCacheTest.java
  git commit -m "feat(xml-signing): add CscCredentialInfoCache to fetch signing cert from CSC credentials/info"
  ```

---

## Task 6: Domain Records Cleanup + CscDomainValueObjectsTest (TDD)

**Note**: After this task the codebase will have compile errors in `CscAuthorizationAdapter`, `CscSignatureAdapter`, `XmlSigningServiceImpl`, and `SagaCommandHandler`. These are fixed in Tasks 7, 8, and 9.

**Files:**
- Modify: `src/test/java/com/wpanther/xmlsigning/application/dto/csc/CscDomainValueObjectsTest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/dto/csc/CscAuthorizeCommand.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/dto/csc/CscAuthorizeResult.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/dto/csc/CscSignHashCommand.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/dto/csc/CscSignHashResult.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/usecase/SigningResult.java`

- [ ] **Step 1: Replace `CscDomainValueObjectsTest.java`**

  ```java
  package com.wpanther.xmlsigning.application.dto.csc;

  import org.junit.jupiter.api.Test;
  import java.util.List;
  import static org.assertj.core.api.Assertions.*;

  class CscDomainValueObjectsTest {

      @Test
      void cscAuthorizeCommand_storesAllFields() {
          var cmd = new CscAuthorizeCommand(
              "cred-1", "2.16.840.1.101.3.4.2.1",
              List.of("abc123"), "1234", "Thai e-Tax signing");
          assertThat(cmd.credentialId()).isEqualTo("cred-1");
          assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
          assertThat(cmd.hashes()).containsExactly("abc123");
          assertThat(cmd.pin()).isEqualTo("1234");
          assertThat(cmd.description()).isEqualTo("Thai e-Tax signing");
      }

      @Test
      void cscAuthorizeCommand_acceptsNullPin() {
          var cmd = new CscAuthorizeCommand(
              "cred-1", "2.16.840.1.101.3.4.2.1",
              List.of("abc123"), null, "description");
          assertThat(cmd.pin()).isNull();
      }

      @Test
      void cscAuthorizeResult_storesSadToken() {
          var result = new CscAuthorizeResult("sad-token-xyz");
          assertThat(result.sadToken()).isEqualTo("sad-token-xyz");
      }

      @Test
      void cscSignHashCommand_storesAllFields() {
          var cmd = new CscSignHashCommand(
              "cred-1", "sad-token", "2.16.840.1.101.3.4.2.1",
              List.of("digest1"));
          assertThat(cmd.credentialId()).isEqualTo("cred-1");
          assertThat(cmd.sadToken()).isEqualTo("sad-token");
          assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
          assertThat(cmd.hashes()).containsExactly("digest1");
      }

      @Test
      void cscSignHashResult_storesSignaturesAndResponseId() {
          var result = new CscSignHashResult(List.of("sig1"), "resp-id-001");
          assertThat(result.signatures()).containsExactly("sig1");
          assertThat(result.responseId()).isEqualTo("resp-id-001");
      }
  }
  ```

- [ ] **Step 2: Run test — expect compilation failures (old record fields)**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscDomainValueObjectsTest 2>&1 | grep -E "ERROR|cannot find" | head -10
  ```

  Expected: compilation errors — `hashAlgorithmOid()`, `hashes()`, `pin()` not yet defined on records.

- [ ] **Step 3: Replace `CscAuthorizeCommand.java`**

  ```java
  package com.wpanther.xmlsigning.application.dto.csc;

  import java.util.List;

  public record CscAuthorizeCommand(
      String credentialId,
      String hashAlgorithmOid,
      List<String> hashes,
      String pin,
      String description
  ) {}
  ```

- [ ] **Step 4: Replace `CscAuthorizeResult.java`**

  ```java
  package com.wpanther.xmlsigning.application.dto.csc;

  public record CscAuthorizeResult(String sadToken) {}
  ```

- [ ] **Step 5: Replace `CscSignHashCommand.java`**

  ```java
  package com.wpanther.xmlsigning.application.dto.csc;

  import java.util.List;

  public record CscSignHashCommand(
      String credentialId,
      String sadToken,
      String hashAlgorithmOid,
      List<String> hashes
  ) {}
  ```

- [ ] **Step 6: Replace `CscSignHashResult.java`**

  ```java
  package com.wpanther.xmlsigning.application.dto.csc;

  import java.util.List;

  public record CscSignHashResult(List<String> signatures, String responseId) {}
  ```

- [ ] **Step 7: Replace `SigningResult.java`**

  ```java
  package com.wpanther.xmlsigning.application.usecase;

  public record SigningResult(
          String signedXml,
          String certificate,
          String responseId
  ) {
      public SigningResult {
          requireNonBlank(signedXml, "signedXml");
          requireNonBlank(responseId, "responseId");
      }

      private static void requireNonBlank(String value, String fieldName) {
          if (value == null || value.isBlank()) {
              throw new IllegalArgumentException(fieldName + " cannot be null or blank");
          }
      }
  }
  ```

- [ ] **Step 8: Run `CscDomainValueObjectsTest` — expect green (other compile errors are expected)**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscDomainValueObjectsTest
  ```

  Expected: BUILD SUCCESS, 5 tests PASS. (Maven compiles only source needed for this test class.)

- [ ] **Step 9: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/application/dto/csc/ \
          src/main/java/com/wpanther/xmlsigning/application/usecase/SigningResult.java \
          src/test/java/com/wpanther/xmlsigning/application/dto/csc/CscDomainValueObjectsTest.java
  git commit -m "refactor(xml-signing): update domain records for CSC v2.0 — remove clientId, simplify commands"
  ```

---

## Task 7: `CscAuthorizationAdapter` + `CscAuthorizationAdapterTest` (TDD)

**Files:**
- Modify: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapterTest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`

- [ ] **Step 1: Replace `CscAuthorizationAdapterTest.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
  import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.ArgumentCaptor;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.util.List;

  import static org.assertj.core.api.Assertions.*;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  class CscAuthorizationAdapterTest {

      @Mock
      private CSCAuthClient feignClient;

      @InjectMocks
      private CscAuthorizationAdapter adapter;

      @Test
      void authorize_mapsCommandToFeignRequestAndReturnsSadToken() {
          CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
          feignResponse.setSAD("sad-token-abc");
          when(feignClient.authorize(any())).thenReturn(feignResponse);

          CscAuthorizeCommand cmd = new CscAuthorizeCommand(
              "cred-1", "2.16.840.1.101.3.4.2.1",
              List.of("digest1"), null, "Thai e-Tax signing");
          CscAuthorizeResult result = adapter.authorize(cmd);

          assertThat(result.sadToken()).isEqualTo("sad-token-abc");

          ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
          verify(feignClient).authorize(captor.capture());
          CSCAuthorizeRequest captured = captor.getValue();
          assertThat(captured.getCredentialID()).isEqualTo("cred-1");
          assertThat(captured.getHashAlgorithmOID()).isEqualTo("2.16.840.1.101.3.4.2.1");
          assertThat(captured.getHashes()).containsExactly("digest1");
          assertThat(captured.getNumSignatures()).isEqualTo(1);
          assertThat(captured.getAuthData()).isNull();
      }

      @Test
      void authorize_includesAuthDataWhenPinNonBlank() {
          CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
          feignResponse.setSAD("sad-with-pin");
          when(feignClient.authorize(any())).thenReturn(feignResponse);

          CscAuthorizeCommand cmd = new CscAuthorizeCommand(
              "cred-1", "2.16.840.1.101.3.4.2.1",
              List.of("digest1"), "1234", "signing");
          adapter.authorize(cmd);

          ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
          verify(feignClient).authorize(captor.capture());
          assertThat(captor.getValue().getAuthData()).isNotNull().hasSize(1);
          assertThat(captor.getValue().getAuthData().get(0).getId()).isEqualTo("PIN");
          assertThat(captor.getValue().getAuthData().get(0).getValue()).isEqualTo("1234");
      }

      @Test
      void authorize_omitsAuthDataWhenPinBlank() {
          CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
          feignResponse.setSAD("sad-no-pin");
          when(feignClient.authorize(any())).thenReturn(feignResponse);

          CscAuthorizeCommand cmd = new CscAuthorizeCommand(
              "cred-1", "2.16.840.1.101.3.4.2.1",
              List.of("digest1"), "", "signing");
          adapter.authorize(cmd);

          ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
          verify(feignClient).authorize(captor.capture());
          assertThat(captor.getValue().getAuthData()).isNull();
      }

      @Test
      void authorize_propagatesExceptionWhenClientThrows() {
          when(feignClient.authorize(any())).thenThrow(new RuntimeException("CSC unavailable"));

          CscAuthorizeCommand cmd = new CscAuthorizeCommand(
              "cred-1", "2.16.840.1.101.3.4.2.1",
              List.of("digest1"), null, "description");

          assertThatThrownBy(() -> adapter.authorize(cmd))
              .isInstanceOf(RuntimeException.class)
              .hasMessageContaining("CSC unavailable");
      }

      @Test
      void authorize_mapsMultipleDigests() {
          CSCAuthorizeResponse feignResponse = new CSCAuthorizeResponse();
          feignResponse.setSAD("sad-multi");
          when(feignClient.authorize(any())).thenReturn(feignResponse);

          CscAuthorizeCommand cmd = new CscAuthorizeCommand(
              "cred-2", "2.16.840.1.101.3.4.2.1",
              List.of("digest-a", "digest-b"), null, "Multi-doc signing");
          adapter.authorize(cmd);

          ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
          verify(feignClient).authorize(captor.capture());
          assertThat(captor.getValue().getHashes()).containsExactly("digest-a", "digest-b");
          assertThat(captor.getValue().getNumSignatures()).isEqualTo(1);
          assertThat(captor.getValue().getDescription()).isEqualTo("Multi-doc signing");
      }
  }
  ```

- [ ] **Step 2: Run test — expect compilation failure (old field names in adapter)**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscAuthorizationAdapterTest 2>&1 | grep -E "ERROR|cannot find" | head -10
  ```

  Expected: compilation errors in `CscAuthorizationAdapter` — `command.clientId()`, `command.numSignatures()`, `command.hashAlgorithm()`, `command.documentDigests()`, `response.getTransactionID()` no longer exist.

- [ ] **Step 3: Replace `CscAuthorizationAdapter.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

  import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
  import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
  import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
  import feign.FeignException;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.List;

  @Component
  @RequiredArgsConstructor
  @Slf4j
  public class CscAuthorizationAdapter implements CscAuthorizationPort {

      private final CSCAuthClient feignClient;

      @Override
      public CscAuthorizeResult authorize(CscAuthorizeCommand command) throws CscAuthorizationException {
          log.debug("Delegating authorization to CSC API for credentialId={}", command.credentialId());

          List<CSCAuthorizeRequest.AuthDataEntry> authData = null;
          if (command.pin() != null && !command.pin().isBlank()) {
              authData = List.of(CSCAuthorizeRequest.AuthDataEntry.builder()
                      .id("PIN").value(command.pin()).build());
          }

          CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
                  .credentialID(command.credentialId())
                  .numSignatures(1)
                  .hashAlgorithmOID(command.hashAlgorithmOid())
                  .hashes(command.hashes().toArray(new String[0]))
                  .authData(authData)
                  .description(command.description())
                  .build();

          try {
              CSCAuthorizeResponse response = feignClient.authorize(request);
              validateResponse(response, command.credentialId());
              return new CscAuthorizeResult(response.getSAD());
          } catch (FeignException e) {
              log.error("CSC authorization failed for credentialId={}: {}",
                      command.credentialId(), e.getMessage(), e);
              throw new CscAuthorizationException(
                      "CSC authorization failed: " + e.getMessage(),
                      e, null, command.credentialId());
          }
      }

      private void validateResponse(CSCAuthorizeResponse response, String credentialId) {
          if (response.getSAD() == null || response.getSAD().isBlank()) {
              throw new CscAuthorizationException(
                      "CSC authorization response missing SAD token",
                      null, credentialId);
          }
      }
  }
  ```

- [ ] **Step 4: Run `CscAuthorizationAdapterTest` — expect all green**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscAuthorizationAdapterTest
  ```

  Expected: BUILD SUCCESS, 5 tests PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java \
          src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapterTest.java
  git commit -m "refactor(xml-signing): update CscAuthorizationAdapter for CSC v2.0 — authData PIN, no clientId"
  ```

---

## Task 8: `CscSignatureAdapter` + `CscSignatureAdapterTest` (TDD)

**Files:**
- Modify: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapterTest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`

- [ ] **Step 1: Replace `CscSignatureAdapterTest.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

  import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
  import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.ArgumentCaptor;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.util.List;

  import static org.assertj.core.api.Assertions.*;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  class CscSignatureAdapterTest {

      @Mock
      private CSCSignatureClient feignClient;

      @InjectMocks
      private CscSignatureAdapter adapter;

      @Test
      void signHash_mapsCommandToFeignRequestAndReturnsResult() {
          CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                  .signatures(new String[]{"base64-signature-value"})
                  .responseID("resp-001")
                  .build();
          when(feignClient.signHash(any())).thenReturn(feignResponse);

          CscSignHashCommand cmd = new CscSignHashCommand(
                  "cred-1", "sad-token-xyz",
                  "2.16.840.1.101.3.4.2.1", List.of("digest-abc"));

          CscSignHashResult result = adapter.signHash(cmd);

          assertThat(result.signatures()).containsExactly("base64-signature-value");
          assertThat(result.responseId()).isEqualTo("resp-001");
      }

      @Test
      void signHash_mapsTopLevelFieldsCorrectly() {
          CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                  .signatures(new String[]{"sig-1"})
                  .responseID("resp-002")
                  .build();
          when(feignClient.signHash(any())).thenReturn(feignResponse);

          CscSignHashCommand cmd = new CscSignHashCommand(
                  "my-cred", "my-sad-token",
                  "2.16.840.1.101.3.4.2.1", List.of("digest-1"));

          adapter.signHash(cmd);

          ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
          verify(feignClient).signHash(captor.capture());
          CSCSignatureRequest captured = captor.getValue();

          assertThat(captured.getCredentialID()).isEqualTo("my-cred");
          assertThat(captured.getSAD()).isEqualTo("my-sad-token");
          assertThat(captured.getHashAlgorithmOID()).isEqualTo("2.16.840.1.101.3.4.2.1");
          assertThat(captured.getHashes()).containsExactly("digest-1");
      }

      @Test
      void signHash_hasNoSignatureDataWrapperAndFlatHashesAtRoot() {
          CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                  .signatures(new String[]{"sig-value"})
                  .build();
          when(feignClient.signHash(any())).thenReturn(feignResponse);

          CscSignHashCommand cmd = new CscSignHashCommand(
                  "cred-x", "sad-x",
                  "2.16.840.1.101.3.4.2.1", List.of("hash-x"));

          adapter.signHash(cmd);

          ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
          verify(feignClient).signHash(captor.capture());
          assertThat(captor.getValue().getHashes()).containsExactly("hash-x");
      }

      @Test
      void signHash_convertsMultipleDocumentDigestsToArray() {
          CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
                  .signatures(new String[]{"sig-a", "sig-b"})
                  .build();
          when(feignClient.signHash(any())).thenReturn(feignResponse);

          CscSignHashCommand cmd = new CscSignHashCommand(
                  "cred-m", "sad-m",
                  "2.16.840.1.101.3.4.2.1", List.of("digest-a", "digest-b"));

          CscSignHashResult result = adapter.signHash(cmd);

          assertThat(result.signatures()).containsExactly("sig-a", "sig-b");

          ArgumentCaptor<CSCSignatureRequest> captor = ArgumentCaptor.forClass(CSCSignatureRequest.class);
          verify(feignClient).signHash(captor.capture());
          assertThat(captor.getValue().getHashes()).containsExactly("digest-a", "digest-b");
      }

      @Test
      void signHash_propagatesCscSignatureExceptionWithoutDoubleWrapping() {
          CscSignatureException original = new CscSignatureException(
                  "HSM unavailable", new RuntimeException("timeout"), "txn-99");
          when(feignClient.signHash(any())).thenThrow(original);

          CscSignHashCommand cmd = new CscSignHashCommand(
                  "cred-1", "sad-1",
                  "2.16.840.1.101.3.4.2.1", List.of("digest-1"));

          Throwable thrown = catchThrowable(() -> adapter.signHash(cmd));
          assertThat(thrown).isSameAs(original);
      }

      @Test
      void signHash_wrapsGenericExceptionInCscSignatureException() {
          when(feignClient.signHash(any())).thenThrow(new RuntimeException("network error"));

          CscSignHashCommand cmd = new CscSignHashCommand(
                  "cred-1", "sad-1",
                  "2.16.840.1.101.3.4.2.1", List.of("digest-1"));

          assertThatThrownBy(() -> adapter.signHash(cmd))
                  .isInstanceOf(CscSignatureException.class)
                  .hasMessageContaining("network error");
      }
  }
  ```

- [ ] **Step 2: Run test — expect compilation failure (old field names in adapter)**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscSignatureAdapterTest 2>&1 | grep -E "ERROR|cannot find" | head -10
  ```

  Expected: compilation errors in `CscSignatureAdapter` — `command.clientId()`, `command.hashAlgorithm()`, `command.documentDigests()`, `command.signatureType()`, `command.pin()`, `response.getCertificate()` etc. no longer exist.

- [ ] **Step 3: Replace `CscSignatureAdapter.java`**

  ```java
  package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

  import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
  import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
  import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
  import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.stereotype.Component;

  import java.util.Arrays;

  @Component
  @RequiredArgsConstructor
  @Slf4j
  public class CscSignatureAdapter implements CscSignaturePort {

      private final CSCSignatureClient feignClient;

      @Override
      public CscSignHashResult signHash(CscSignHashCommand command) throws CscSignatureException {
          log.debug("Delegating signHash to CSC API for credentialId={}", command.credentialId());

          CSCSignatureRequest request = CSCSignatureRequest.builder()
                  .credentialID(command.credentialId())
                  .SAD(command.sadToken())
                  .hashAlgorithmOID(command.hashAlgorithmOid())
                  .hashes(command.hashes().toArray(new String[0]))
                  .build();

          try {
              CSCSignatureResponse response = feignClient.signHash(request);
              validateResponse(response);
              return new CscSignHashResult(
                      Arrays.asList(response.getSignatures()),
                      response.getResponseID()
              );
          } catch (CscSignatureException e) {
              throw e;
          } catch (Exception e) {
              log.error("CSC signHash failed for credentialId={}", command.credentialId(), e);
              throw new CscSignatureException("CSC signHash failed: " + e.getMessage(), e);
          }
      }

      private void validateResponse(CSCSignatureResponse response) {
          if (response.getSignatures() == null || response.getSignatures().length == 0) {
              throw new CscSignatureException("CSC signHash response missing signatures");
          }
      }
  }
  ```

- [ ] **Step 4: Run `CscSignatureAdapterTest` — expect all green**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=CscSignatureAdapterTest
  ```

  Expected: BUILD SUCCESS, 6 tests PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java \
          src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapterTest.java
  git commit -m "refactor(xml-signing): update CscSignatureAdapter for CSC v2.0 — flat hashes, no signatureData wrapper"
  ```

---

## Task 9: `XmlSigningServiceImpl`, `SagaCommandHandler` + `XmlSigningServiceImplTest` (TDD)

This task fixes the final compile errors left from Tasks 6–8: `XmlSigningServiceImpl` (all stale field references + adds `CscCredentialInfoCache`) and `SagaCommandHandler` (1-line fix: `transactionId()` → `responseId()`).

**Files:**
- Modify: `src/test/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImplTest.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImpl.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/usecase/SagaCommandHandler.java`

- [ ] **Step 1: Replace `XmlSigningServiceImplTest.java`**

  ```java
  package com.wpanther.xmlsigning.application.usecase;

  import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
  import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
  import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
  import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
  import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
  import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscCredentialInfoCache;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.DisplayName;
  import org.junit.jupiter.api.Nested;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.ArgumentCaptor;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;
  import org.springframework.test.util.ReflectionTestUtils;

  import java.nio.charset.StandardCharsets;
  import java.security.MessageDigest;
  import java.util.Base64;
  import java.util.List;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.ArgumentMatchers.anyString;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  @DisplayName("XmlSigningServiceImpl")
  class XmlSigningServiceImplTest {

      @Mock private CscSignaturePort signaturePort;
      @Mock private CscAuthorizationPort authorizationPort;
      @Mock private XadesEmbeddingPort xadesEmbeddingPort;
      @Mock private CscCredentialInfoCache credentialInfoCache;

      @InjectMocks
      private XmlSigningServiceImpl signingService;

      @BeforeEach
      void setUp() {
          ReflectionTestUtils.setField(signingService, "credentialId", "test-credential");
          ReflectionTestUtils.setField(signingService, "hashAlgorithmOid", "2.16.840.1.101.3.4.2.1");
          ReflectionTestUtils.setField(signingService, "digestAlgorithm", "SHA-256");
          ReflectionTestUtils.setField(signingService, "pin", "");
          lenient().when(credentialInfoCache.getCertificate()).thenReturn("cached-cert-base64");
      }

      @Nested
      @DisplayName("signXml() Method")
      class SignXmlMethod {

          @Test
          @DisplayName("Signs XML successfully with valid response")
          void signXml_happyPath_returnsSigningResult() throws Exception {
              CscAuthorizeResult authResult = new CscAuthorizeResult("test-sad-token");
              CscSignHashResult signResult = new CscSignHashResult(
                  List.of("base64-encoded-signature"), "resp-001");

              when(authorizationPort.authorize(any(CscAuthorizeCommand.class))).thenReturn(authResult);
              when(signaturePort.signHash(any(CscSignHashCommand.class))).thenReturn(signResult);
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed><ds:Signature>test</ds:Signature></signed>"
                      .getBytes(StandardCharsets.UTF_8));

              SigningResult result = signingService.signXml("<xml>test</xml>", "doc-1");

              assertThat(result).isNotNull();
              assertThat(result.signedXml()).contains("ds:Signature");
              assertThat(result.certificate()).isEqualTo("cached-cert-base64");
              assertThat(result.responseId()).isEqualTo("resp-001");
              verify(credentialInfoCache).getCertificate();
              verify(authorizationPort, never()).authorize(
                  argThat(c -> c.credentialId() == null));
          }

          @Test
          @DisplayName("Cert comes from cache, not from signHash response")
          void signXml_certificateComesFromCache() throws Exception {
              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
              when(signaturePort.signHash(any())).thenReturn(
                  new CscSignHashResult(List.of("sig"), "resp-cert-test"));
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

              signingService.signXml("<xml/>", "doc-cert");

              verify(credentialInfoCache).getCertificate();
              ArgumentCaptor<String> certCaptor = ArgumentCaptor.forClass(String.class);
              verify(xadesEmbeddingPort).embedSignature(any(), any(), anyString(),
                  certCaptor.capture(), anyString());
              assertThat(certCaptor.getValue()).isEqualTo("cached-cert-base64");
          }

          @Test
          @DisplayName("authorize request must have credentialId, hashAlgorithmOid, hashes — no clientId")
          void signXml_sendsCorrectAuthorizeCommand() throws Exception {
              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
              when(signaturePort.signHash(any())).thenReturn(
                  new CscSignHashResult(List.of("sig"), "resp-auth-test"));
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

              signingService.signXml("<xml>test</xml>", "doc-1");

              ArgumentCaptor<CscAuthorizeCommand> captor =
                  ArgumentCaptor.forClass(CscAuthorizeCommand.class);
              verify(authorizationPort).authorize(captor.capture());
              CscAuthorizeCommand cmd = captor.getValue();

              assertThat(cmd.credentialId()).isEqualTo("test-credential");
              assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
              assertThat(cmd.hashes()).isNotEmpty();
              assertThat(cmd.pin()).isNullOrEmpty();
              assertThat(cmd.description()).isEqualTo("Thai e-Tax Invoice XML Signing");
          }

          @Test
          @DisplayName("authorize request includes pin when pin is configured")
          void signXml_includesPinInAuthorizeCommand() throws Exception {
              ReflectionTestUtils.setField(signingService, "pin", "secret-pin");
              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
              when(signaturePort.signHash(any())).thenReturn(
                  new CscSignHashResult(List.of("sig"), "resp-pin-test"));
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

              signingService.signXml("<xml/>", "doc-pin");

              ArgumentCaptor<CscAuthorizeCommand> captor =
                  ArgumentCaptor.forClass(CscAuthorizeCommand.class);
              verify(authorizationPort).authorize(captor.capture());
              assertThat(captor.getValue().pin()).isEqualTo("secret-pin");
          }

          @Test
          @DisplayName("signHash command must have credentialId, sadToken, hashAlgorithmOid, hashes")
          void signXml_sendsCorrectSignHashCommand() throws Exception {
              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("test-sad-xyz"));
              when(signaturePort.signHash(any())).thenReturn(
                  new CscSignHashResult(List.of("sig"), "resp-sign-test"));
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

              signingService.signXml("<xml>test</xml>", "doc-1");

              ArgumentCaptor<CscSignHashCommand> captor =
                  ArgumentCaptor.forClass(CscSignHashCommand.class);
              verify(signaturePort).signHash(captor.capture());
              CscSignHashCommand cmd = captor.getValue();

              assertThat(cmd.credentialId()).isEqualTo("test-credential");
              assertThat(cmd.sadToken()).isEqualTo("test-sad-xyz");
              assertThat(cmd.hashAlgorithmOid()).isEqualTo("2.16.840.1.101.3.4.2.1");
              assertThat(cmd.hashes()).isNotEmpty();
          }

          @Test
          @DisplayName("responseId in SigningResult comes from signHash response")
          void signXml_responseIdFromSignHash() throws Exception {
              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
              when(signaturePort.signHash(any())).thenReturn(
                  new CscSignHashResult(List.of("sig"), "CSC-RESPONSE-ABC"));
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

              SigningResult result = signingService.signXml("<xml/>", "doc-1");

              assertThat(result.responseId()).isEqualTo("CSC-RESPONSE-ABC");
          }

          @Test
          @DisplayName("Calculates correct document digest")
          void signXml_calculatesCorrectDigest() throws Exception {
              String xmlContent = "<xml>test</xml>";
              MessageDigest digest = MessageDigest.getInstance("SHA-256");
              byte[] hash = digest.digest(xmlContent.getBytes(StandardCharsets.UTF_8));
              String expectedDigest = Base64.getEncoder().encodeToString(hash);

              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
              when(signaturePort.signHash(any())).thenReturn(
                  new CscSignHashResult(List.of("sig"), "resp-digest-test"));
              when(xadesEmbeddingPort.embedSignature(any(), any(), anyString(), anyString(), anyString()))
                  .thenReturn("<signed/>".getBytes(StandardCharsets.UTF_8));

              signingService.signXml(xmlContent, "doc-1");

              ArgumentCaptor<CscAuthorizeCommand> authCaptor =
                  ArgumentCaptor.forClass(CscAuthorizeCommand.class);
              verify(authorizationPort).authorize(authCaptor.capture());
              assertThat(authCaptor.getValue().hashes().get(0)).isEqualTo(expectedDigest);
          }

          @Test
          @DisplayName("Throws CscAuthorizationException when authorization port throws")
          void signXml_authorizationFails_throwsCscAuthorizationException() {
              CscAuthorizationException authException = new CscAuthorizationException(
                      "Authorization denied", null, null, "test-credential");
              when(authorizationPort.authorize(any())).thenThrow(authException);

              assertThatThrownBy(() -> signingService.signXml("<xml>test</xml>", "doc-auth-fail"))
                      .isInstanceOf(CscAuthorizationException.class)
                      .hasMessageContaining("Authorization denied");
          }

          @Test
          @DisplayName("Throws CscSignatureException when signature port throws")
          void signXml_signingFails_throwsCscSignatureException() {
              when(authorizationPort.authorize(any())).thenReturn(new CscAuthorizeResult("sad"));
              CscSignatureException signException = new CscSignatureException("HSM unavailable", null);
              when(signaturePort.signHash(any())).thenThrow(signException);

              assertThatThrownBy(() -> signingService.signXml("<xml>test</xml>", "doc-sign-fail"))
                      .isInstanceOf(CscSignatureException.class)
                      .hasMessageContaining("HSM unavailable");
          }

          @Test
          @DisplayName("Wraps generic authorization exception in CscAuthorizationException")
          void signXml_genericAuthException_wrappedAsCscAuthorizationException() {
              when(authorizationPort.authorize(any())).thenThrow(new RuntimeException("Authorize failed"));

              assertThatThrownBy(() -> signingService.signXml("<xml/>", "doc-1"))
                      .isInstanceOf(CscAuthorizationException.class)
                      .hasMessageContaining("CSC authorization failed");
          }
      }
  }
  ```

- [ ] **Step 2: Run test — expect compilation failure (XmlSigningServiceImpl still has old field references)**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=XmlSigningServiceImplTest 2>&1 | grep -E "ERROR|cannot find" | head -10
  ```

  Expected: compilation errors — `clientId`, `signatureLevel`, `CscAuthorizeResult.transactionId()`, `CscSignHashResult.certificate()`, `SigningResult.transactionId` constructor arg no longer exist.

- [ ] **Step 3: Replace `XmlSigningServiceImpl.java`**

  ```java
  package com.wpanther.xmlsigning.application.usecase;

  import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
  import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
  import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
  import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
  import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
  import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
  import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.CscCredentialInfoCache;
  import lombok.RequiredArgsConstructor;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.annotation.Value;
  import org.springframework.stereotype.Service;

  import java.nio.charset.StandardCharsets;
  import java.security.MessageDigest;
  import java.util.Base64;
  import java.util.List;

  @Service
  @RequiredArgsConstructor
  @Slf4j
  public class XmlSigningServiceImpl implements XmlSigningService {

      private final CscSignaturePort signaturePort;
      private final CscAuthorizationPort authorizationPort;
      private final XadesEmbeddingPort xadesEmbeddingPort;
      private final CscCredentialInfoCache credentialInfoCache;

      @Value("${app.csc.credential-id}")
      private String credentialId;

      @Value("${app.csc.hash-algorithm-oid:2.16.840.1.101.3.4.2.1}")
      private String hashAlgorithmOid;

      @Value("${app.csc.digest-algorithm:SHA-256}")
      private String digestAlgorithm;

      @Value("${app.csc.pin:}")
      private String pin;

      @Override
      public SigningResult signXml(String xmlContent, String documentId) {
          log.info("Starting XML signing process for document: {}", documentId);

          try {
              String certificate = credentialInfoCache.getCertificate();
              String documentDigest = calculateDigest(xmlContent);
              log.debug("Computed digest for document: {}", documentId);

              CscAuthorizeResult authResult;
              try {
                  CscAuthorizeCommand authCommand = new CscAuthorizeCommand(
                          credentialId, hashAlgorithmOid,
                          List.of(documentDigest),
                          (pin != null && !pin.isBlank()) ? pin : null,
                          "Thai e-Tax Invoice XML Signing");
                  authResult = authorizationPort.authorize(authCommand);
                  log.debug("Received SAD token from CSC API for document: {}", documentId);
              } catch (CscAuthorizationException e) {
                  throw e;
              } catch (Exception e) {
                  log.error("CSC authorization failed for credential {} document {}",
                          credentialId, documentId, e);
                  throw new CscAuthorizationException(
                          "CSC authorization failed: " + e.getMessage(), e, null, credentialId);
              }

              CscSignHashResult signResult;
              try {
                  CscSignHashCommand signCommand = new CscSignHashCommand(
                          credentialId, authResult.sadToken(),
                          hashAlgorithmOid, List.of(documentDigest));
                  signResult = signaturePort.signHash(signCommand);
              } catch (CscSignatureException e) {
                  throw e;
              } catch (Exception e) {
                  log.error("CSC signHash failed for document {}", documentId, e);
                  throw new CscSignatureException("CSC signHash failed: " + e.getMessage(), e, null);
              }

              String rawSignature = signResult.signatures().get(0);
              byte[] signedXmlBytes = xadesEmbeddingPort.embedSignature(
                      xmlContent.getBytes(StandardCharsets.UTF_8),
                      rawSignature.getBytes(StandardCharsets.UTF_8),
                      documentDigest,
                      certificate,
                      documentId
              );
              String signedXml = new String(signedXmlBytes, StandardCharsets.UTF_8);

              log.info("Document signed successfully: {}", documentId);
              return new SigningResult(signedXml, certificate, signResult.responseId());

          } catch (CscAuthorizationException | CscSignatureException e) {
              throw e;
          } catch (Exception e) {
              log.error("Failed to sign XML document: {}", documentId, e);
              throw new CscSignatureException("XML signing failed: " + e.getMessage(), e, null);
          }
      }

      private String calculateDigest(String content) throws Exception {
          MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
          byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
          return Base64.getEncoder().encodeToString(hash);
      }
  }
  ```

- [ ] **Step 4: Fix `SagaCommandHandler.java` — update single reference to `signingResult.transactionId()`**

  In `SagaCommandHandler.java`, find line:
  ```java
  transactionId = signingResult.transactionId();
  ```

  Replace with:
  ```java
  transactionId = signingResult.responseId();
  ```

  The local variable `transactionId` and its usage throughout the method remain unchanged — only this one getter call changes.

- [ ] **Step 5: Run `XmlSigningServiceImplTest` — expect all green**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test -Dtest=XmlSigningServiceImplTest
  ```

  Expected: BUILD SUCCESS, all tests PASS.

- [ ] **Step 6: Verify full compilation succeeds**

  ```bash
  mvn compile -q
  ```

  Expected: BUILD SUCCESS with no errors. This is the first clean compile since Task 6.

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImpl.java \
          src/main/java/com/wpanther/xmlsigning/application/usecase/SagaCommandHandler.java \
          src/test/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImplTest.java
  git commit -m "feat(xml-signing): update XmlSigningServiceImpl for CSC v2.0 — cached cert, authData PIN, responseId"
  ```

---

## Task 10: Full Test Suite + Stale Reference Check

- [ ] **Step 1: Run all unit tests**

  ```bash
  cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
  mvn test 2>&1 | tail -40
  ```

  Expected: BUILD SUCCESS. All tests PASS. If any test fails, fix it before continuing.

- [ ] **Step 2: Check for any remaining stale CSC v1 references in main sources**

  ```bash
  grep -rn "getClientId\|clientId\(\)\|getHashAlgo\b\|operationID\|transactionID\|signatureData\|hashToSign\|\.hash\b\|getTransactionID\|getCertificate\(\)" \
    src/main/java \
    --include="*.java"
  ```

  Expected: no output. If any matches appear, fix them.

- [ ] **Step 3: Check for stale v1 config keys in yml files**

  ```bash
  grep -n "client-id:\|hash-algorithm:\s\|signature-level:" \
    src/main/resources/application.yml \
    src/test/resources/application-full-integration-test.yml
  ```

  Expected: no output.

- [ ] **Step 4: Final commit (only if steps 2–3 produced output and required fixes)**

  ```bash
  git add -p
  git commit -m "fix(xml-signing): remove remaining stale CSC v1 references"
  ```

- [ ] **Step 5: Confirm all tasks complete — summary of commits**

  ```bash
  git log --oneline -10
  ```

  Expected commits (most recent first):
  ```
  fix(xml-signing): remove remaining stale CSC v1 references   ← only if needed
  feat(xml-signing): update XmlSigningServiceImpl for CSC v2.0 — cached cert, authData PIN, responseId
  refactor(xml-signing): update CscSignatureAdapter for CSC v2.0 — flat hashes, no signatureData wrapper
  refactor(xml-signing): update CscAuthorizationAdapter for CSC v2.0 — authData PIN, no clientId
  refactor(xml-signing): update domain records for CSC v2.0 — remove clientId, simplify commands
  feat(xml-signing): add CscCredentialInfoCache to fetch signing cert from CSC credentials/info
  feat(xml-signing): add credentials/info DTOs and Feign client for CSC v2.0
  refactor(xml-signing): update CSC Feign DTOs for v2.0 wire format
  refactor(xml-signing): rename CSC config properties for v2.0 compliance
  build(xml-signing): bump testcontainers 1.19.7 → 2.0.5
  ```
