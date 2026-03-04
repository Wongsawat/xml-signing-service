# Hexagonal Architecture Migration — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate `xml-signing-service` and `pdf-signing-service` to strict hexagonal architecture where domain ports use only domain types, all outbound dependencies are behind port interfaces, and Camel routes call inbound port interfaces.

**Architecture:** Incremental-by-port — each task completes and passes tests independently. Domain value objects are created first so ports can use them; infrastructure adapters are wired last. No database schema changes. No Kafka topic changes.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Mockito, AssertJ, MapStruct, Lombok. Test runner: `mvn test`. Integration tests: `mvn test -Pintegration`.

**Design doc:** `docs/plans/2026-03-04-hexagonal-migration-design.md`

---

## Context: What Already Exists

**xml-signing-service violations to fix:**
- `CscAuthorizationPort` / `CscSignaturePort` import infra DTOs → need domain value objects
- `CSCAuthClient extends CscAuthorizationPort` / `CSCSignatureClient extends CscSignaturePort` → Feign clients must become plain clients; introduce adapter classes
- `XmlSigningServiceImpl` lives in `infrastructure/client/` but implements domain service → move to `application/service/`
- No ports for MinIO storage, event publishing, saga reply publishing — `SagaCommandHandler` directly depends on concrete infra classes
- No inbound port for Camel route

**pdf-signing-service violations to fix (smaller scope — already partially hexagonal):**
- `SagaCommandPort` is in `application/port/` instead of `domain/port/in/`
- `SagaCommandHandler` depends on `PdfSigningEventPublisher` (infrastructure class)
- `SignedPdfDocumentRepository` in `domain/repository/` instead of `domain/port/out/`

**Run all unit tests after every task:**
```bash
cd services/xml-signing-service && mvn test
```

---

# PART 1: xml-signing-service

---

## Task 1: Create CSC domain value objects

**Why:** `CscAuthorizationPort` and `CscSignaturePort` currently import `CSCAuthorizeRequest/Response` and `CSCSignatureRequest/Response` from `infrastructure.client.csc.dto`. We need domain-level records so ports are infra-free.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/domain/model/csc/CscAuthorizeCommand.java`
- Create: `src/main/java/com/wpanther/xmlsigning/domain/model/csc/CscAuthorizeResult.java`
- Create: `src/main/java/com/wpanther/xmlsigning/domain/model/csc/CscSignHashCommand.java`
- Create: `src/main/java/com/wpanther/xmlsigning/domain/model/csc/CscSignHashResult.java`
- Create: `src/test/java/com/wpanther/xmlsigning/domain/model/csc/CscDomainValueObjectsTest.java`

**Step 1: Write the failing test**

```java
// src/test/java/com/wpanther/xmlsigning/domain/model/csc/CscDomainValueObjectsTest.java
package com.wpanther.xmlsigning.domain.model.csc;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CscDomainValueObjectsTest {

    @Test
    void cscAuthorizeCommand_storesAllFields() {
        var cmd = new CscAuthorizeCommand(
            "client-1", "cred-1", "1", "SHA256",
            new String[]{"abc123"}, "Thai e-Tax signing");
        assertThat(cmd.clientId()).isEqualTo("client-1");
        assertThat(cmd.credentialId()).isEqualTo("cred-1");
        assertThat(cmd.documentDigests()).containsExactly("abc123");
    }

    @Test
    void cscAuthorizeResult_storesAllFields() {
        var result = new CscAuthorizeResult("sad-token-xyz", "txn-001");
        assertThat(result.sadToken()).isEqualTo("sad-token-xyz");
        assertThat(result.transactionId()).isEqualTo("txn-001");
    }

    @Test
    void cscSignHashCommand_storesAllFields() {
        var cmd = new CscSignHashCommand(
            "client-1", "cred-1", "sad-token", "SHA256withRSA",
            new String[]{"digest1"}, "XAdES", "XAdES-BASELINE-T",
            "enveloped", "SHA256", System.currentTimeMillis());
        assertThat(cmd.clientId()).isEqualTo("client-1");
        assertThat(cmd.sadToken()).isEqualTo("sad-token");
    }

    @Test
    void cscSignHashResult_storesAllFields() {
        var result = new CscSignHashResult(new String[]{"sig1"}, "cert-pem");
        assertThat(result.signatures()).containsExactly("sig1");
        assertThat(result.certificate()).isEqualTo("cert-pem");
    }
}
```

**Step 2: Run to verify it fails**
```bash
mvn test -Dtest=CscDomainValueObjectsTest
```
Expected: FAIL — `cannot find symbol: CscAuthorizeCommand`

**Step 3: Create the four records**

```java
// domain/model/csc/CscAuthorizeCommand.java
package com.wpanther.xmlsigning.domain.model.csc;

public record CscAuthorizeCommand(
    String clientId,
    String credentialId,
    String numSignatures,
    String hashAlgorithm,
    String[] documentDigests,
    String description
) {}
```

```java
// domain/model/csc/CscAuthorizeResult.java
package com.wpanther.xmlsigning.domain.model.csc;

public record CscAuthorizeResult(String sadToken, String transactionId) {}
```

```java
// domain/model/csc/CscSignHashCommand.java
package com.wpanther.xmlsigning.domain.model.csc;

public record CscSignHashCommand(
    String clientId,
    String credentialId,
    String sadToken,
    String hashAlgorithm,
    String[] documentDigests,
    String signatureType,
    String signatureLevel,
    String signatureForm,
    String digestAlgorithm,
    long signDate
) {}
```

```java
// domain/model/csc/CscSignHashResult.java
package com.wpanther.xmlsigning.domain.model.csc;

public record CscSignHashResult(String[] signatures, String certificate) {}
```

**Step 4: Run to verify it passes**
```bash
mvn test -Dtest=CscDomainValueObjectsTest
```
Expected: PASS

**Step 5: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/domain/model/csc/ \
        src/test/java/com/wpanther/xmlsigning/domain/model/csc/
git commit -m "feat(xml-signing): add CSC domain value objects for clean port contracts"
```

---

## Task 2: Clean up CscAuthorizationPort + introduce CscAuthorizationAdapter

**Why:** `CscAuthorizationPort.authorize()` currently takes `CSCAuthorizeRequest` (infra DTO). Replace with `CscAuthorizeCommand` (domain type). `CSCAuthClient` currently `extends CscAuthorizationPort` — remove that and create a separate adapter class.

**Files:**
- Modify: `src/main/java/com/wpanther/xmlsigning/domain/port/CscAuthorizationPort.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/CSCAuthClient.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`
- Create: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapterTest.java`

**Step 1: Write the failing adapter test**

```java
// infrastructure/adapter/out/csc/CscAuthorizationAdapterTest.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CscAuthorizationAdapterTest {

    @Mock private CSCAuthClient feignClient;
    @InjectMocks private CscAuthorizationAdapter adapter;

    @Test
    void authorize_mapsCommandToFeignRequestAndReturnsResult() {
        CSCAuthorizeResponse feignResponse = CSCAuthorizeResponse.builder()
            .SAD("sad-token-abc")
            .transactionID("txn-123")
            .build();
        when(feignClient.authorize(any())).thenReturn(feignResponse);

        CscAuthorizeCommand cmd = new CscAuthorizeCommand(
            "client-1", "cred-1", "1", "SHA256",
            new String[]{"digest1"}, "description");
        CscAuthorizeResult result = adapter.authorize(cmd);

        assertThat(result.sadToken()).isEqualTo("sad-token-abc");
        assertThat(result.transactionId()).isEqualTo("txn-123");

        ArgumentCaptor<CSCAuthorizeRequest> captor = ArgumentCaptor.forClass(CSCAuthorizeRequest.class);
        verify(feignClient).authorize(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo("client-1");
        assertThat(captor.getValue().getCredentialID()).isEqualTo("cred-1");
    }
}
```

**Step 2: Run to verify it fails**
```bash
mvn test -Dtest=CscAuthorizationAdapterTest
```
Expected: FAIL — `CscAuthorizationAdapter` not found

**Step 3: Update the port interface**

```java
// domain/port/CscAuthorizationPort.java  — remove all infra imports
package com.wpanther.xmlsigning.domain.port;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeResult;

public interface CscAuthorizationPort {
    CscAuthorizeResult authorize(CscAuthorizeCommand command) throws CscAuthorizationException;
}
```

**Step 4: Create the adapter**

```java
// infrastructure/adapter/out/csc/CscAuthorizationAdapter.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.exception.CscAuthorizationException;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.domain.port.CscAuthorizationPort;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CscAuthorizationAdapter implements CscAuthorizationPort {

    private final CSCAuthClient feignClient;

    @Override
    public CscAuthorizeResult authorize(CscAuthorizeCommand command) throws CscAuthorizationException {
        CSCAuthorizeRequest request = CSCAuthorizeRequest.builder()
            .clientId(command.clientId())
            .credentialID(command.credentialId())
            .numSignatures(command.numSignatures())
            .hashAlgo(command.hashAlgorithm())
            .hash(command.documentDigests())
            .description(command.description())
            .build();

        CSCAuthorizeResponse response = feignClient.authorize(request);
        return new CscAuthorizeResult(response.getSAD(), response.getTransactionID());
    }
}
```

**Step 5: Update CSCAuthClient — remove `extends CscAuthorizationPort`**

```java
// Change: public interface CSCAuthClient extends CscAuthorizationPort {
// To:     public interface CSCAuthClient {
//
// Remove the @Override annotation from the authorize() method.
// Keep the @PostMapping and method signature using CSCAuthorizeRequest/Response unchanged.
```

In `CSCAuthClient.java`, change line 33 from:
```java
public interface CSCAuthClient extends CscAuthorizationPort {
```
to:
```java
public interface CSCAuthClient {
```
And remove the `@Override` annotation from the `authorize()` method.

**Step 6: Run to verify all tests pass**
```bash
mvn test
```
Expected: All tests PASS. (Note: `XmlSigningServiceImplTest` will need updating in Task 4 because it currently mocks `CSCAuthClient` as a `CscAuthorizationPort`.)

**Step 7: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/domain/port/CscAuthorizationPort.java \
        src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java \
        src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/CSCAuthClient.java \
        src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapterTest.java
git commit -m "feat(xml-signing): introduce CscAuthorizationAdapter; clean infra DTOs from port"
```

---

## Task 3: Clean up CscSignaturePort + introduce CscSignatureAdapter

**Why:** Same violation as Task 2, for the signature port. `CscSignaturePort.signHash()` takes `CSCSignatureRequest` (infra DTO). `CSCSignatureClient extends CscSignaturePort` — remove that.

**Files:**
- Modify: `src/main/java/com/wpanther/xmlsigning/domain/port/CscSignaturePort.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/CSCSignatureClient.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`
- Create: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapterTest.java`

**Step 1: Write the failing adapter test**

```java
// infrastructure/adapter/out/csc/CscSignatureAdapterTest.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.model.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashResult;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CscSignatureAdapterTest {

    @Mock private CSCSignatureClient feignClient;
    @InjectMocks private CscSignatureAdapter adapter;

    @Test
    void signHash_mapsCommandAndReturnsResult() {
        CSCSignatureResponse feignResponse = CSCSignatureResponse.builder()
            .signatures(new String[]{"raw-sig-base64"})
            .certificate("cert-pem")
            .build();
        when(feignClient.signHash(any())).thenReturn(feignResponse);

        CscSignHashCommand cmd = new CscSignHashCommand(
            "client-1", "cred-1", "sad-token", "SHA256withRSA",
            new String[]{"digest-abc"}, "XAdES", "XAdES-BASELINE-T",
            "enveloped", "SHA256", System.currentTimeMillis());

        CscSignHashResult result = adapter.signHash(cmd);

        assertThat(result.signatures()).containsExactly("raw-sig-base64");
        assertThat(result.certificate()).isEqualTo("cert-pem");
    }
}
```

**Step 2: Run to verify it fails**
```bash
mvn test -Dtest=CscSignatureAdapterTest
```

**Step 3: Update port interface**

```java
// domain/port/CscSignaturePort.java
package com.wpanther.xmlsigning.domain.port;

import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashResult;

public interface CscSignaturePort {
    CscSignHashResult signHash(CscSignHashCommand command) throws CscSignatureException;
}
```

**Step 4: Create the adapter**

```java
// infrastructure/adapter/out/csc/CscSignatureAdapter.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc;

import com.wpanther.xmlsigning.domain.exception.CscSignatureException;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashResult;
import com.wpanther.xmlsigning.domain.port.CscSignaturePort;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.SignatureAttributes;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.SignatureData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CscSignatureAdapter implements CscSignaturePort {

    private final CSCSignatureClient feignClient;

    @Override
    public CscSignHashResult signHash(CscSignHashCommand command) throws CscSignatureException {
        SignatureAttributes attrs = SignatureAttributes.builder()
            .signatureType(command.signatureType())
            .signatureLevel(command.signatureLevel())
            .signatureForm(command.signatureForm())
            .digestAlgorithm(command.digestAlgorithm())
            .signDate(command.signDate())
            .build();

        SignatureData data = SignatureData.builder()
            .hashToSign(command.documentDigests())
            .signatureAttributes(attrs)
            .build();

        CSCSignatureRequest request = CSCSignatureRequest.builder()
            .clientId(command.clientId())
            .credentialID(command.credentialId())
            .SAD(command.sadToken())
            .hashAlgo(command.hashAlgorithm())
            .signatureData(data)
            .build();

        CSCSignatureResponse response = feignClient.signHash(request);
        return new CscSignHashResult(response.getSignatures(), response.getCertificate());
    }
}
```

**Step 5: Update CSCSignatureClient — remove `extends CscSignaturePort`**

In `CSCSignatureClient.java`, change:
```java
public interface CSCSignatureClient extends CscSignaturePort {
```
to:
```java
public interface CSCSignatureClient {
```
And remove the `@Override` annotation from `signHash()`.

**Step 6: Run all tests**
```bash
mvn test
```
Expected: PASS (compiler may show errors in `XmlSigningServiceImpl` — fix those in Task 4)

**Step 7: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/domain/port/CscSignaturePort.java \
        src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java \
        src/main/java/com/wpanther/xmlsigning/infrastructure/client/csc/CSCSignatureClient.java \
        src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapterTest.java
git commit -m "feat(xml-signing): introduce CscSignatureAdapter; clean infra DTOs from port"
```

---

## Task 4: Update XmlSigningServiceImpl to use new domain types; move to application/service/

**Why:** `XmlSigningServiceImpl` lives in `infrastructure.client` but implements `XmlSigningService` (domain service interface). It must be moved to `application.service`. Its internal logic calling `CscAuthorizationPort` and `CscSignaturePort` must be updated to use the new `CscAuthorize*` / `CscSignHash*` domain types.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImpl.java` (moved + updated)
- Delete: `src/main/java/com/wpanther/xmlsigning/infrastructure/client/XmlSigningServiceImpl.java`
- Modify: `src/test/java/com/wpanther/xmlsigning/infrastructure/client/XmlSigningServiceImplTest.java` → move to `application/service/`

**Step 1: Write the updated test in new location**

```java
// src/test/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImplTest.java
package com.wpanther.xmlsigning.application.service;

import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.domain.model.csc.CscSignHashResult;
import com.wpanther.xmlsigning.domain.port.CscAuthorizationPort;
import com.wpanther.xmlsigning.domain.port.CscSignaturePort;
import com.wpanther.xmlsigning.domain.service.SigningResult;
import com.wpanther.xmlsigning.infrastructure.embedder.XadesSignatureEmbedder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("XmlSigningServiceImpl (application layer)")
class XmlSigningServiceImplTest {

    // Note: mock the PORT interfaces, not the Feign client implementations
    @Mock private CscSignaturePort signaturePort;
    @Mock private CscAuthorizationPort authorizationPort;
    @Mock private XadesSignatureEmbedder signatureEmbedder;
    @InjectMocks private XmlSigningServiceImpl signingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(signingService, "clientId", "test-client");
        ReflectionTestUtils.setField(signingService, "credentialId", "test-cred");
        ReflectionTestUtils.setField(signingService, "hashAlgorithm", "SHA-256withRSA");
        ReflectionTestUtils.setField(signingService, "signatureLevel", "XAdES-BASELINE-T");
        ReflectionTestUtils.setField(signingService, "digestAlgorithm", "SHA256");
    }

    @Test
    @DisplayName("signXml returns SigningResult with signed XML, certificate, and transactionId")
    void signXml_returnsSigningResult() throws Exception {
        when(authorizationPort.authorize(any()))
            .thenReturn(new CscAuthorizeResult("sad-token", "txn-001"));
        when(signaturePort.signHash(any()))
            .thenReturn(new CscSignHashResult(new String[]{"raw-sig"}, "cert-pem"));
        when(signatureEmbedder.embedSignature(any(), any(), any(), any()))
            .thenReturn("<signedXml/>");

        SigningResult result = signingService.signXml("<xml/>", "doc-1");

        assertThat(result.signedXml()).isEqualTo("<signedXml/>");
        assertThat(result.certificate()).isEqualTo("cert-pem");
        assertThat(result.transactionId()).isEqualTo("txn-001");
    }
}
```

**Step 2: Run to verify it fails**
```bash
mvn test -Dtest="com.wpanther.xmlsigning.application.service.XmlSigningServiceImplTest"
```

**Step 3: Create updated XmlSigningServiceImpl in application/service/**

Copy `infrastructure/client/XmlSigningServiceImpl.java` to `application/service/XmlSigningServiceImpl.java`, then:
- Update `package` to `com.wpanther.xmlsigning.application.service`
- Update the `signHash()` private method to construct `CscAuthorizeCommand` + `CscSignHashCommand` (domain records) instead of `CSCAuthorizeRequest` + `CSCSignatureRequest` (infra DTOs)
- Remove `CscApiClient` / legacy client imports

Key change in `signHash()`:
```java
// OLD: authorizationPort.authorize(CSCAuthorizeRequest.builder()...build())
// NEW: authorizationPort.authorize(new CscAuthorizeCommand(clientId, credentialId, "1",
//          digestAlgorithm, new String[]{documentDigest}, "Thai e-Tax Invoice XML Signing"))

// OLD: signaturePort.signHash(CSCSignatureRequest.builder()...SAD(authResponse.getSAD())...build())
// NEW: signaturePort.signHash(new CscSignHashCommand(clientId, credentialId, authResult.sadToken(),
//          hashAlgorithm, new String[]{documentDigest}, "XAdES", signatureLevel,
//          "enveloped", digestAlgorithm, System.currentTimeMillis()))

// OLD: String rawSignature = apiResponse.signatureResponse().getSignatures()[0]
// NEW: String rawSignature = signHashResult.signatures()[0]
//      String certificate = signHashResult.certificate()
```

**Step 4: Delete the old file**
```bash
git rm src/main/java/com/wpanther/xmlsigning/infrastructure/client/XmlSigningServiceImpl.java
git rm src/test/java/com/wpanther/xmlsigning/infrastructure/client/XmlSigningServiceImplTest.java
```

**Step 5: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 6: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImpl.java \
        src/test/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImplTest.java
git commit -m "feat(xml-signing): move XmlSigningServiceImpl to application layer; use domain port types"
```

---

## Task 5: Add XmlStoragePort + MinioXmlStorageAdapter

**Why:** `SagaCommandHandler` directly calls `MinioStorageService` (concrete infra class). Introduce a domain port for storage and an adapter that wraps the existing `MinioStorageService`.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/domain/model/XmlStorageKey.java`
- Create: `src/main/java/com/wpanther/xmlsigning/domain/port/out/XmlStoragePort.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/storage/MinioXmlStorageAdapter.java`
- Create: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/storage/MinioXmlStorageAdapterTest.java`
- Create: `src/test/java/com/wpanther/xmlsigning/domain/model/XmlStorageKeyTest.java`

**Step 1: Write the failing tests**

```java
// domain/model/XmlStorageKeyTest.java
package com.wpanther.xmlsigning.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class XmlStorageKeyTest {

    @Test
    void constructor_storesValue() {
        XmlStorageKey key = new XmlStorageKey("2024/01/15/TAX_INVOICE/file.xml");
        assertThat(key.value()).isEqualTo("2024/01/15/TAX_INVOICE/file.xml");
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThatThrownBy(() -> new XmlStorageKey(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_rejectsBlankValue() {
        assertThatThrownBy(() -> new XmlStorageKey("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

```java
// infrastructure/adapter/out/storage/MinioXmlStorageAdapterTest.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.storage;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioXmlStorageAdapterTest {

    @Mock private MinioStorageService minioService;
    @InjectMocks private MinioXmlStorageAdapter adapter;

    @Test
    void uploadOriginalXml_returnsStorageKey() {
        when(minioService.uploadOriginalXml("inv-1", "TAX_INVOICE", "<xml/>"))
            .thenReturn("2024/01/01/TAX_INVOICE/original-file.xml");

        XmlStorageKey key = adapter.uploadOriginalXml("inv-1", DocumentType.TAX_INVOICE, "<xml/>");

        assertThat(key.value()).isEqualTo("2024/01/01/TAX_INVOICE/original-file.xml");
    }

    @Test
    void uploadSignedXml_returnsStorageKey() {
        when(minioService.upload("inv-1", "TAX_INVOICE", "<signedXml/>"))
            .thenReturn("2024/01/01/TAX_INVOICE/signed-file.xml");

        XmlStorageKey key = adapter.uploadSignedXml("inv-1", DocumentType.TAX_INVOICE, "<signedXml/>");

        assertThat(key.value()).isEqualTo("2024/01/01/TAX_INVOICE/signed-file.xml");
    }

    @Test
    void buildUrl_delegatesToMinioService() {
        when(minioService.buildUrl("path/file.xml")).thenReturn("http://minio/path/file.xml");

        String url = adapter.buildUrl(new XmlStorageKey("path/file.xml"));

        assertThat(url).isEqualTo("http://minio/path/file.xml");
    }

    @Test
    void delete_delegatesToMinioService() {
        adapter.delete(new XmlStorageKey("path/file.xml"));
        verify(minioService).delete("path/file.xml");
    }
}
```

**Step 2: Run to verify both fail**
```bash
mvn test -Dtest="XmlStorageKeyTest,MinioXmlStorageAdapterTest"
```

**Step 3: Create XmlStorageKey**

```java
// domain/model/XmlStorageKey.java
package com.wpanther.xmlsigning.domain.model;

import java.util.Objects;

public record XmlStorageKey(String value) {
    public XmlStorageKey {
        Objects.requireNonNull(value, "XmlStorageKey value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("XmlStorageKey value must not be blank");
    }
}
```

**Step 4: Create XmlStoragePort**

```java
// domain/port/out/XmlStoragePort.java
package com.wpanther.xmlsigning.domain.port.out;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;

public interface XmlStoragePort {
    XmlStorageKey uploadOriginalXml(String invoiceId, DocumentType type, String xmlContent);
    XmlStorageKey uploadSignedXml(String invoiceId, DocumentType type, String xmlContent);
    String buildUrl(XmlStorageKey key);
    void delete(XmlStorageKey key);
}
```

**Step 5: Create MinioXmlStorageAdapter**

```java
// infrastructure/adapter/out/storage/MinioXmlStorageAdapter.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.storage;

import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.model.XmlStorageKey;
import com.wpanther.xmlsigning.domain.port.out.XmlStoragePort;
import com.wpanther.xmlsigning.infrastructure.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinioXmlStorageAdapter implements XmlStoragePort {

    private final MinioStorageService minioService;

    @Override
    public XmlStorageKey uploadOriginalXml(String invoiceId, DocumentType type, String xmlContent) {
        return new XmlStorageKey(minioService.uploadOriginalXml(invoiceId, type.name(), xmlContent));
    }

    @Override
    public XmlStorageKey uploadSignedXml(String invoiceId, DocumentType type, String xmlContent) {
        return new XmlStorageKey(minioService.upload(invoiceId, type.name(), xmlContent));
    }

    @Override
    public String buildUrl(XmlStorageKey key) {
        return minioService.buildUrl(key.value());
    }

    @Override
    public void delete(XmlStorageKey key) {
        minioService.delete(key.value());
    }
}
```

**Step 6: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 7: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/domain/model/XmlStorageKey.java \
        src/main/java/com/wpanther/xmlsigning/domain/port/out/XmlStoragePort.java \
        src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/storage/ \
        src/test/java/com/wpanther/xmlsigning/domain/model/XmlStorageKeyTest.java \
        src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/storage/
git commit -m "feat(xml-signing): add XmlStoragePort + MinioXmlStorageAdapter"
```

---

## Task 6: Add XmlSignedEventPort and SagaReplyPort interfaces

**Why:** Create the two outbound port interfaces that will replace direct `EventPublisher` / `SagaReplyPublisher` dependencies in `SagaCommandHandler`. Pure additions — no existing code changes yet.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/domain/port/out/XmlSignedEventPort.java`
- Create: `src/main/java/com/wpanther/xmlsigning/domain/port/out/SagaReplyPort.java`

**Step 1: Create both port interfaces** (no test needed — pure interfaces; tested via adapter tests in Task 7)

```java
// domain/port/out/XmlSignedEventPort.java
package com.wpanther.xmlsigning.domain.port.out;

import com.wpanther.xmlsigning.domain.model.DocumentType;

public interface XmlSignedEventPort {
    void publishXmlSigned(String invoiceId, String invoiceNumber,
                          DocumentType documentType, String correlationId);
}
```

```java
// domain/port/out/SagaReplyPort.java
package com.wpanther.xmlsigning.domain.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

public interface SagaReplyPort {
    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                        String signedXmlUrl, Long signedXmlSize);
    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId,
                        String errorMessage);
    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
```

**Step 2: Run all tests to confirm nothing broken**
```bash
mvn test
```

**Step 3: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/domain/port/out/XmlSignedEventPort.java \
        src/main/java/com/wpanther/xmlsigning/domain/port/out/SagaReplyPort.java
git commit -m "feat(xml-signing): add XmlSignedEventPort and SagaReplyPort interfaces"
```

---

## Task 7: Create messaging adapters; delete EventPublisher + SagaReplyPublisher

**Why:** The two messaging port interfaces from Task 6 need infrastructure adapter implementations. The adapters talk directly to `OutboxService` (no intermediate wrapper classes). Then `EventPublisher` and `SagaReplyPublisher` are deleted since their logic moves into the adapters.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapter.java`
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`
- Create: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapterTest.java`
- Create: `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapterTest.java`
- Delete: `src/main/java/com/wpanther/xmlsigning/infrastructure/messaging/EventPublisher.java`
- Delete: `src/main/java/com/wpanther/xmlsigning/infrastructure/messaging/SagaReplyPublisher.java`

**Step 1: Write failing tests for both adapters**

```java
// OutboxXmlSignedEventAdapterTest.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxXmlSignedEventAdapterTest {

    @Mock private OutboxService outboxService;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OutboxXmlSignedEventAdapter adapter;

    @Test
    void publishXmlSigned_savesToOutbox() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publishXmlSigned("inv-1", "T001", DocumentType.TAX_INVOICE, "corr-1");

        verify(outboxService).saveWithRouting(
            any(), eq("SignedXmlDocument"), eq("inv-1"),
            eq("xml.signed"), eq("inv-1"), anyString());
    }
}
```

```java
// OutboxSagaReplyAdapterTest.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxSagaReplyAdapterTest {

    @Mock private OutboxService outboxService;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private OutboxSagaReplyAdapter adapter;

    @Test
    void publishSuccess_savesToOutboxWithCorrectTopic() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publishSuccess("saga-1", SagaStep.SIGN_XML, "corr-1",
                "http://minio/signed.xml", 1024L);

        verify(outboxService).saveWithRouting(
            any(), eq("SignedXmlDocument"), eq("saga-1"),
            eq("saga.reply.xml-signing"), eq("saga-1"), anyString());
    }

    @Test
    void publishFailure_savesToOutbox() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publishFailure("saga-1", SagaStep.SIGN_XML, "corr-1", "error msg");

        verify(outboxService).saveWithRouting(any(), any(), any(),
                eq("saga.reply.xml-signing"), any(), any());
    }
}
```

**Step 2: Run to verify failures**
```bash
mvn test -Dtest="OutboxXmlSignedEventAdapterTest,OutboxSagaReplyAdapterTest"
```

**Step 3: Create OutboxXmlSignedEventAdapter**

Copy the logic from the existing `EventPublisher.publishXmlSigned()` method, but implement `XmlSignedEventPort`:

```java
// infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapter.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.event.XmlSignedEvent;
import com.wpanther.xmlsigning.domain.model.DocumentType;
import com.wpanther.xmlsigning.domain.port.out.XmlSignedEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxXmlSignedEventAdapter implements XmlSignedEventPort {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishXmlSigned(String invoiceId, String invoiceNumber,
                                 DocumentType documentType, String correlationId) {
        XmlSignedEvent event = new XmlSignedEvent(invoiceId, invoiceNumber,
                documentType.name(), correlationId);
        Map<String, String> headers = Map.of(
            "correlationId", correlationId,
            "invoiceNumber", invoiceNumber
        );
        outboxService.saveWithRouting(event, "SignedXmlDocument", invoiceId,
                "xml.signed", invoiceId, toJson(headers));
        log.info("Published XmlSignedEvent to outbox: {}", invoiceNumber);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox headers", e);
        }
    }
}
```

**Step 4: Create OutboxSagaReplyAdapter**

Copy the logic from existing `SagaReplyPublisher`, implement `SagaReplyPort`:

```java
// infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java
package com.wpanther.xmlsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.xmlsigning.domain.event.XmlSigningReplyEvent;
import com.wpanther.xmlsigning.domain.port.out.SagaReplyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxSagaReplyAdapter implements SagaReplyPort {

    private static final String REPLY_TOPIC = "saga.reply.xml-signing";
    private static final String AGGREGATE_TYPE = "SignedXmlDocument";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                               String signedXmlUrl, Long signedXmlSize) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.success(sagaId, sagaStep, correlationId,
                signedXmlUrl, signedXmlSize);
        outboxService.saveWithRouting(reply, AGGREGATE_TYPE, sagaId, REPLY_TOPIC, sagaId,
                toJson(Map.of("sagaId", sagaId, "correlationId", correlationId, "status", "SUCCESS")));
        log.info("Published SUCCESS reply: saga={} step={}", sagaId, sagaStep);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);
        outboxService.saveWithRouting(reply, AGGREGATE_TYPE, sagaId, REPLY_TOPIC, sagaId,
                toJson(Map.of("sagaId", sagaId, "correlationId", correlationId, "status", "FAILURE")));
        log.info("Published FAILURE reply: saga={} step={}: {}", sagaId, sagaStep, errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.compensated(sagaId, sagaStep, correlationId);
        outboxService.saveWithRouting(reply, AGGREGATE_TYPE, sagaId, REPLY_TOPIC, sagaId,
                toJson(Map.of("sagaId", sagaId, "correlationId", correlationId, "status", "COMPENSATED")));
        log.info("Published COMPENSATED reply: saga={} step={}", sagaId, sagaStep);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox headers", e);
        }
    }
}
```

**Step 5: Update any tests that directly import EventPublisher / SagaReplyPublisher**

Check for references:
```bash
grep -r "EventPublisher\|SagaReplyPublisher" src/test/ --include="*.java" -l
```
Update those tests to use `XmlSignedEventPort` / `SagaReplyPort` mocks instead.

**Step 6: Delete the old publisher classes**
```bash
git rm src/main/java/com/wpanther/xmlsigning/infrastructure/messaging/EventPublisher.java
git rm src/main/java/com/wpanther/xmlsigning/infrastructure/messaging/SagaReplyPublisher.java
```

**Step 7: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 8: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/ \
        src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/
git commit -m "feat(xml-signing): add messaging adapters; delete EventPublisher + SagaReplyPublisher"
```

---

## Task 8: Add SagaCommandPort (inbound); update SagaCommandHandler

**Why:** Introduce the inbound port so Camel routes depend on the interface, not the concrete handler. Update `SagaCommandHandler` to implement the port and replace its three concrete infra dependencies (`MinioStorageService`, `EventPublisher`, `SagaReplyPublisher`) with the port interfaces from Tasks 5–7.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/domain/port/in/SagaCommandPort.java`
- Modify: `src/main/java/com/wpanther/xmlsigning/application/service/SagaCommandHandler.java`
- Modify: `src/test/java/com/wpanther/xmlsigning/application/service/SagaCommandHandlerTest.java`

**Step 1: Create SagaCommandPort**

```java
// domain/port/in/SagaCommandPort.java
package com.wpanther.xmlsigning.domain.port.in;

import com.wpanther.xmlsigning.domain.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;

public interface SagaCommandPort {
    void handleProcessCommand(ProcessXmlSigningCommand command);
    void handleCompensation(CompensateXmlSigningCommand command);
}
```

**Step 2: Update SagaCommandHandlerTest to use port mocks**

In `SagaCommandHandlerTest.java`:
- Replace `@Mock MinioStorageService minioStorageService` with `@Mock XmlStoragePort xmlStoragePort`
- Replace `@Mock EventPublisher eventPublisher` with `@Mock XmlSignedEventPort xmlSignedEventPort`
- Replace `@Mock SagaReplyPublisher sagaReplyPublisher` with `@Mock SagaReplyPort sagaReplyPort`
- Update all `when(minioStorageService...)` stubs to `when(xmlStoragePort...)`
- Update all `verify(sagaReplyPublisher...)` to `verify(sagaReplyPort...)`
- Update all `verify(eventPublisher...)` to `verify(xmlSignedEventPort...)`

**Step 3: Run test to verify it fails** (SagaCommandHandler still has old deps)
```bash
mvn test -Dtest=SagaCommandHandlerTest
```

**Step 4: Update SagaCommandHandler**

Key changes in `SagaCommandHandler.java`:

```java
// Add to class declaration:
public class SagaCommandHandler implements SagaCommandPort {

// Replace these three fields:
// OLD:
private final MinioStorageService minioStorageService;
private final EventPublisher eventPublisher;
private final SagaReplyPublisher sagaReplyPublisher;
// NEW:
private final XmlStoragePort xmlStoragePort;
private final XmlSignedEventPort xmlSignedEventPort;
private final SagaReplyPort sagaReplyPort;

// In handleProcessCommand(), Phase 2 (upload original XML):
// OLD: originalXmlPath = minioStorageService.uploadOriginalXml(...)
//      originalXmlUrl = minioStorageService.buildUrl(originalXmlPath)
// NEW: XmlStorageKey originalXmlKey = xmlStoragePort.uploadOriginalXml(...)
//      originalXmlUrl = xmlStoragePort.buildUrl(originalXmlKey)
//      originalXmlPath = originalXmlKey.value()

// In TX2 (publish events):
// OLD: eventPublisher.publishXmlSigned(new XmlSignedEvent(...))
//      sagaReplyPublisher.publishSuccess(...)
// NEW: xmlSignedEventPort.publishXmlSigned(command.getDocumentId(), command.getInvoiceNumber(),
//          finalDocumentType, command.getCorrelationId())
//      sagaReplyPort.publishSuccess(command.getSagaId(), command.getSagaStep(),
//          command.getCorrelationId(), signedXmlUrl, signedXmlSize)

// In handleCompensation():
// OLD: minioStorageService.delete(originalXmlPath)
//      sagaReplyPublisher.publishCompensated(...)
// NEW: xmlStoragePort.delete(new XmlStorageKey(originalXmlPath))
//      sagaReplyPort.publishCompensated(...)

// Replace all other sagaReplyPublisher.publishFailure/publishSuccess with sagaReplyPort.*
// Replace all minioStorageService.* calls with xmlStoragePort.* calls
```

**Step 5: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 6: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/domain/port/in/SagaCommandPort.java \
        src/main/java/com/wpanther/xmlsigning/application/service/SagaCommandHandler.java \
        src/test/java/com/wpanther/xmlsigning/application/service/SagaCommandHandlerTest.java
git commit -m "feat(xml-signing): add SagaCommandPort; SagaCommandHandler depends only on ports"
```

---

## Task 9: Move SagaRouteConfig to adapter/in/camel/; inject SagaCommandPort

**Why:** The Camel inbound adapter should live in `infrastructure/adapter/in/camel/` to match the hexagonal structure. It should inject `SagaCommandPort` (the domain port interface), not `SagaCommandHandler` (the concrete class).

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`
- Delete: `src/main/java/com/wpanther/xmlsigning/infrastructure/config/SagaRouteConfig.java`
- Modify: `src/test/java/com/wpanther/xmlsigning/infrastructure/config/SagaRouteConfigTest.java` → move/update

**Step 1: Create new SagaRouteConfig**

Copy `infrastructure/config/SagaRouteConfig.java` to `infrastructure/adapter/in/camel/SagaRouteConfig.java`, then:
- Update `package` declaration
- Change injection: `private final SagaCommandHandler sagaCommandHandler` → `private final SagaCommandPort sagaCommandPort`
- Update Camel route `.bean()` calls to use `sagaCommandPort` instead of `sagaCommandHandler`

**Step 2: Update the route config test**

Move `SagaRouteConfigTest.java` to match the new package. Update the `@Mock` type:
```java
// OLD: @Mock SagaCommandHandler sagaCommandHandler;
// NEW: @Mock SagaCommandPort sagaCommandPort;
```

**Step 3: Delete old SagaRouteConfig**
```bash
git rm src/main/java/com/wpanther/xmlsigning/infrastructure/config/SagaRouteConfig.java
git rm src/test/java/com/wpanther/xmlsigning/infrastructure/config/SagaRouteConfigTest.java
```

**Step 4: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 5: Commit**
```bash
git add src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/ \
        src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/
git commit -m "feat(xml-signing): move SagaRouteConfig to adapter/in/camel; inject SagaCommandPort"
```

---

## Task 10: Move SignedXmlDocumentRepository to domain/port/out/

**Why:** Repository interfaces are outbound ports in hexagonal architecture. Moving it makes the `domain/port/out/` package the single home for all secondary ports.

**Files:**
- Create: `src/main/java/com/wpanther/xmlsigning/domain/port/out/SignedXmlDocumentRepository.java`
- Delete: `src/main/java/com/wpanther/xmlsigning/domain/repository/SignedXmlDocumentRepository.java`
- Modify: All files importing the old package

**Step 1: Copy the interface to the new location**

```java
// domain/port/out/SignedXmlDocumentRepository.java
package com.wpanther.xmlsigning.domain.port.out;

// Same content as existing domain/repository/SignedXmlDocumentRepository.java
// Just update the package declaration
```

**Step 2: Find all files importing the old package**
```bash
grep -r "domain.repository.SignedXmlDocumentRepository" src/ --include="*.java" -l
```

**Step 3: Update imports in all found files**

For each file, change:
```java
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
```
to:
```java
import com.wpanther.xmlsigning.domain.port.out.SignedXmlDocumentRepository;
```

**Step 4: Delete old file and package**
```bash
git rm src/main/java/com/wpanther/xmlsigning/domain/repository/SignedXmlDocumentRepository.java
```

**Step 5: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 6: Run integration tests**
```bash
mvn test -Pintegration
```
Expected: All PASS (if integration containers are running; otherwise skip and note)

**Step 7: Commit**
```bash
git add -A
git commit -m "feat(xml-signing): move SignedXmlDocumentRepository to domain/port/out"
```

---

## Task 11: Final verification — xml-signing-service complete

**Step 1: Run full test suite including coverage**
```bash
mvn verify
```
Expected: All tests PASS, 80%+ line coverage per package

**Step 2: Verify no direct infra imports remain in domain/port/**
```bash
grep -r "infrastructure" src/main/java/com/wpanther/xmlsigning/domain/ --include="*.java"
```
Expected: No matches

**Step 3: Verify SagaCommandHandler has no direct infra deps**
```bash
grep -E "MinioStorageService|EventPublisher|SagaReplyPublisher" \
  src/main/java/com/wpanther/xmlsigning/application/service/SagaCommandHandler.java
```
Expected: No matches

**Step 4: Commit (if any cleanup needed)**
```bash
git commit -m "chore(xml-signing): hexagonal migration complete — all ports clean"
```

---

# PART 2: pdf-signing-service

The pdf-signing-service is already partially hexagonal. Remaining violations:
1. `SagaCommandPort` in `application/port/` instead of `domain/port/in/`
2. `SagaCommandHandler` depends on `PdfSigningEventPublisher` (infra class) — no domain port
3. `SignedPdfDocumentRepository` in `domain/repository/` instead of `domain/port/out/`

Switch to the pdf-signing-service directory for all remaining tasks:
```bash
cd ../pdf-signing-service
```

---

## Task 12: Move SagaCommandPort to domain/port/in/

**Why:** The inbound port belongs in the domain layer, not the application layer.

**Files:**
- Create: `src/main/java/com/wpanther/pdfsigning/domain/port/in/SagaCommandPort.java`
- Delete: `src/main/java/com/wpanther/pdfsigning/application/port/SagaCommandPort.java`
- Modify: `SagaCommandHandler.java` and `SagaCommandKafkaAdapter.java` (import update)

**Step 1: Create the port in the new location**

```java
// domain/port/in/SagaCommandPort.java
package com.wpanther.pdfsigning.domain.port.in;

import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;

public interface SagaCommandPort {
    void handleProcessPdfSigning(ProcessPdfSigningCommand command);
    void handleCompensatePdfSigning(CompensatePdfSigningCommand command);
}
```

**Step 2: Find all files importing the old package**
```bash
grep -r "application.port.SagaCommandPort" src/ --include="*.java" -l
```

**Step 3: Update imports**

For each file found, change:
```java
import com.wpanther.pdfsigning.application.port.SagaCommandPort;
```
to:
```java
import com.wpanther.pdfsigning.domain.port.in.SagaCommandPort;
```

**Step 4: Delete old port**
```bash
git rm src/main/java/com/wpanther/pdfsigning/application/port/SagaCommandPort.java
```

**Step 5: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 6: Commit**
```bash
git add -A
git commit -m "feat(pdf-signing): move SagaCommandPort to domain/port/in"
```

---

## Task 13: Add PdfSignedEventPort; replace PdfSigningEventPublisher

**Why:** `SagaCommandHandler` (application layer) directly depends on `PdfSigningEventPublisher` (infrastructure class). Introduce a domain port and an adapter.

**Files:**
- Create: `src/main/java/com/wpanther/pdfsigning/domain/port/out/PdfSignedEventPort.java`
- Create: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java`
- Create: `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapterTest.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java`

**Step 1: Write failing adapter test**

```java
// OutboxPdfSignedEventAdapterTest.java
package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPdfSignedEventAdapterTest {

    @Mock private PdfSigningEventPublisher publisher;
    @InjectMocks private OutboxPdfSignedEventAdapter adapter;

    @Test
    void publishSuccess_delegatesToPublisher() {
        adapter.publishSuccess("saga-1", SagaStep.SIGN_PDF, "corr-1",
            "inv-1", "T001", "TAX_INVOICE", "doc-1",
            "http://url/signed.pdf", 2048L, "txn-1", "cert", "PAdES-BASELINE-T",
            Instant.now());

        verify(publisher).publishSuccess(
            eq("saga-1"), eq(SagaStep.SIGN_PDF), eq("corr-1"),
            eq("inv-1"), eq("T001"), eq("TAX_INVOICE"),
            eq("doc-1"), eq("http://url/signed.pdf"), eq(2048L),
            eq("txn-1"), eq("cert"), eq("PAdES-BASELINE-T"), any(Instant.class));
    }

    @Test
    void publishFailure_delegatesToPublisher() {
        adapter.publishFailure("saga-1", SagaStep.SIGN_PDF, "corr-1",
            "inv-1", "T001", "TAX_INVOICE", "error");

        verify(publisher).publishFailure("saga-1", SagaStep.SIGN_PDF, "corr-1",
            "inv-1", "T001", "TAX_INVOICE", "error");
    }
}
```

**Step 2: Run to verify it fails**
```bash
mvn test -Dtest=OutboxPdfSignedEventAdapterTest
```

**Step 3: Create PdfSignedEventPort**

```java
// domain/port/out/PdfSignedEventPort.java
package com.wpanther.pdfsigning.domain.port.out;

import com.wpanther.saga.domain.enums.SagaStep;
import java.time.Instant;

public interface PdfSignedEventPort {
    void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                        String invoiceId, String invoiceNumber, String documentType,
                        String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
                        String transactionId, String certificate, String signatureLevel,
                        Instant signatureTimestamp);

    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId,
                        String invoiceId, String invoiceNumber, String documentType,
                        String errorMessage);

    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
```

**Step 4: Create OutboxPdfSignedEventAdapter**

```java
// infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java
package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.wpanther.pdfsigning.domain.port.out.PdfSignedEventPort;
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;
import com.wpanther.saga.domain.enums.SagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OutboxPdfSignedEventAdapter implements PdfSignedEventPort {

    private final PdfSigningEventPublisher publisher;

    @Override
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                               String invoiceId, String invoiceNumber, String documentType,
                               String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
                               String transactionId, String certificate, String signatureLevel,
                               Instant signatureTimestamp) {
        publisher.publishSuccess(sagaId, sagaStep, correlationId, invoiceId, invoiceNumber,
                documentType, signedDocumentId, signedPdfUrl, signedPdfSize,
                transactionId, certificate, signatureLevel, signatureTimestamp);
    }

    @Override
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId,
                               String invoiceId, String invoiceNumber, String documentType,
                               String errorMessage) {
        publisher.publishFailure(sagaId, sagaStep, correlationId, invoiceId, invoiceNumber,
                documentType, errorMessage);
    }

    @Override
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        publisher.publishCompensated(sagaId, sagaStep, correlationId);
    }
}
```

**Step 5: Update SagaCommandHandler — inject PdfSignedEventPort instead of PdfSigningEventPublisher**

In `SagaCommandHandler.java`:
```java
// OLD:
private final PdfSigningEventPublisher eventPublisher;

// NEW:
private final PdfSignedEventPort eventPublisher;
```
(No method call changes needed — the port signature matches the publisher.)

**Step 6: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 7: Commit**
```bash
git add -A
git commit -m "feat(pdf-signing): add PdfSignedEventPort + adapter; SagaCommandHandler depends on port"
```

---

## Task 14: Move SignedPdfDocumentRepository to domain/port/out/

**Why:** Same pattern as Task 10 — repository interfaces are outbound ports.

**Files:**
- Create: `src/main/java/com/wpanther/pdfsigning/domain/port/out/SignedPdfDocumentRepository.java`
- Delete: `src/main/java/com/wpanther/pdfsigning/domain/repository/SignedPdfDocumentRepository.java`

**Step 1: Copy interface to new location**

Same content as `domain/repository/SignedPdfDocumentRepository.java`, with updated package.

**Step 2: Find and update all imports**
```bash
grep -r "domain.repository.SignedPdfDocumentRepository" src/ --include="*.java" -l
```

Update all imports to `com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository`.

**Step 3: Delete old file**
```bash
git rm src/main/java/com/wpanther/pdfsigning/domain/repository/SignedPdfDocumentRepository.java
```

**Step 4: Run all tests**
```bash
mvn test
```
Expected: All PASS

**Step 5: Run full verification**
```bash
mvn verify
```

**Step 6: Commit**
```bash
git add -A
git commit -m "feat(pdf-signing): move SignedPdfDocumentRepository to domain/port/out"
```

---

## Task 15: Final verification — both services complete

**Step 1: Verify domain layers are clean in both services**

```bash
# xml-signing-service — no infra imports in domain/
grep -r "infrastructure" \
  services/xml-signing-service/src/main/java/com/wpanther/xmlsigning/domain/ \
  --include="*.java"

# pdf-signing-service — no infra imports in domain/
grep -r "infrastructure" \
  services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/ \
  --include="*.java"
```
Expected: No matches in either service.

**Step 2: Verify all port/out interfaces have adapters**

```bash
# xml-signing
ls services/xml-signing-service/src/main/java/com/wpanther/xmlsigning/domain/port/out/
# Expected: CscAuthorizationPort.java, CscSignaturePort.java, SagaReplyPort.java,
#           SignedXmlDocumentRepository.java, XmlSignedEventPort.java, XmlStoragePort.java

# pdf-signing
ls services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out/
# Expected: PdfSignedEventPort.java, SignedPdfDocumentRepository.java
```

**Step 3: Run full test suites on both services**
```bash
cd services/xml-signing-service && mvn verify
cd ../pdf-signing-service && mvn verify
```
Expected: All PASS, coverage requirements met.

**Step 4: Tag migration complete**
```bash
git tag hexagonal-migration-complete
```

---

## Summary

| Task | Service | Key Change |
|------|---------|---|
| 1 | xml-signing | Create 4 CSC domain value object records |
| 2 | xml-signing | CscAuthorizationPort uses domain types; CscAuthorizationAdapter wraps Feign |
| 3 | xml-signing | CscSignaturePort uses domain types; CscSignatureAdapter wraps Feign |
| 4 | xml-signing | Move XmlSigningServiceImpl to application/service/; update to use new port types |
| 5 | xml-signing | Add XmlStorageKey + XmlStoragePort + MinioXmlStorageAdapter |
| 6 | xml-signing | Add XmlSignedEventPort + SagaReplyPort interfaces |
| 7 | xml-signing | OutboxXmlSignedEventAdapter + OutboxSagaReplyAdapter; delete old publishers |
| 8 | xml-signing | Add SagaCommandPort; SagaCommandHandler implements it; use all new ports |
| 9 | xml-signing | Move SagaRouteConfig to adapter/in/camel/; inject SagaCommandPort |
| 10 | xml-signing | Move SignedXmlDocumentRepository to domain/port/out/ |
| 11 | xml-signing | Final verification — domain clean, coverage passes |
| 12 | pdf-signing | Move SagaCommandPort from application/port/ to domain/port/in/ |
| 13 | pdf-signing | Add PdfSignedEventPort + adapter; SagaCommandHandler depends on port |
| 14 | pdf-signing | Move SignedPdfDocumentRepository to domain/port/out/ |
| 15 | both | Final verification — domain clean, coverage passes |
