# Hexagonal Architecture Canonical Alignment — xml-signing-service

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete canonical hexagonal alignment for xml-signing-service by relocating misplaced classes, extracting one new port, and deleting one legacy class — zero business logic changes.

**Architecture:** Six phases: (1) move Kafka DTOs + CSC model objects, (2) split domain ports + extract XadesEmbeddingPort + merge usecase, (3) consolidate infrastructure packages + delete legacy, (4) config sub-packages, (5) relocate tests, (6) verify. Each phase ends with a green `mvn test` and a commit.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Apache Santuario 4.0.4, Maven, JaCoCo 80% line coverage

---

## Context for the Implementer

Read the design doc before starting:
`docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`

The service is ~75% through a hexagonal migration. These tasks complete **canonical alignment** only. Most steps are package declaration changes + import updates. Phase 2 introduces one new interface (`XadesEmbeddingPort`) and adds one constructor parameter to `XmlSigningServiceImpl`.

**Base source path:** `src/main/java/com/wpanther/xmlsigning/`
**Base test path:** `src/test/java/com/wpanther/xmlsigning/`
**Root package:** `com.wpanther.xmlsigning`
**Service directory:** `/home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service`

**How to run tests:**
```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn test
```

**How to run full coverage check:**
```bash
mvn verify
```

**Integration tests require a separate profile — do NOT run them during this refactor:**
```bash
mvn test   # unit tests only (default)
```

---

## Task 1: Move Kafka Event DTOs and CSC Model Objects

**Design doc ref:** Phase 1 — Kafka wire DTOs belong in `application/dto/event/`; CSC command/result records are port parameter types belonging in `application/dto/csc/`.

**Source files to move:**

*From `domain/event/` → `application/dto/event/`:*
- `ProcessXmlSigningCommand.java`
- `CompensateXmlSigningCommand.java`
- `XmlSigningReplyEvent.java`
- `XmlSignedEvent.java`
- `XmlSigningRequestedEvent.java`

*From `domain/model/csc/` → `application/dto/csc/`:*
- `CscAuthorizeCommand.java`
- `CscAuthorizeResult.java`
- `CscSignHashCommand.java`
- `CscSignHashResult.java`

**Consumers that need import updates:**
- `src/main/java/com/wpanther/xmlsigning/application/service/SagaCommandHandler.java`
- `src/main/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImpl.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/domain/port/out/CscAuthorizationPort.java`
- `src/main/java/com/wpanther/xmlsigning/domain/port/out/CscSignaturePort.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`

### Step 1: Create new package directories

```bash
mkdir -p src/main/java/com/wpanther/xmlsigning/application/dto/event
mkdir -p src/main/java/com/wpanther/xmlsigning/application/dto/csc
```

### Step 2: Move event DTO files → `application/dto/event/`

For each of the five event files, copy to the new directory and change the package declaration:

```java
// Old:
package com.wpanther.xmlsigning.domain.event;

// New:
package com.wpanther.xmlsigning.application.dto.event;
```

Then delete the originals:
```bash
rm -rf src/main/java/com/wpanther/xmlsigning/domain/event
```

### Step 3: Move CSC model files → `application/dto/csc/`

For each of the four CSC record files, copy to the new directory and change the package declaration:

```java
// Old:
package com.wpanther.xmlsigning.domain.model.csc;

// New:
package com.wpanther.xmlsigning.application.dto.csc;
```

Then delete the originals:
```bash
rm -rf src/main/java/com/wpanther/xmlsigning/domain/model/csc
```

### Step 4: Update imports in `SagaCommandHandler.java`

In `src/main/java/com/wpanther/xmlsigning/application/service/SagaCommandHandler.java`:

```java
// Replace all:
import com.wpanther.xmlsigning.domain.event.
// With:
import com.wpanther.xmlsigning.application.dto.event.
```

### Step 5: Update imports in `XmlSigningServiceImpl.java`

In `src/main/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImpl.java`:

```java
// Replace all:
import com.wpanther.xmlsigning.domain.model.csc.
// With:
import com.wpanther.xmlsigning.application.dto.csc.
```

### Step 6: Update imports in `SagaRouteConfig.java`

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`:

```java
// Replace all domain.event imports:
import com.wpanther.xmlsigning.domain.event.
// With:
import com.wpanther.xmlsigning.application.dto.event.
```

### Step 7: Update imports in messaging adapters

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.event.
// With:
import com.wpanther.xmlsigning.application.dto.event.
```

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.event.
// With:
import com.wpanther.xmlsigning.application.dto.event.
```

### Step 8: Update imports in CSC port interfaces

In `src/main/java/com/wpanther/xmlsigning/domain/port/out/CscAuthorizationPort.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.model.csc.
// With:
import com.wpanther.xmlsigning.application.dto.csc.
```

In `src/main/java/com/wpanther/xmlsigning/domain/port/out/CscSignaturePort.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.model.csc.
// With:
import com.wpanther.xmlsigning.application.dto.csc.
```

### Step 9: Update imports in CSC adapters

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.model.csc.
// With:
import com.wpanther.xmlsigning.application.dto.csc.
```

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.model.csc.
// With:
import com.wpanther.xmlsigning.application.dto.csc.
```

### Step 10: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

Sanity check — no old package references:
```bash
grep -r "domain\.event\." src/main/java/ --include="*.java"
grep -r "domain\.model\.csc\." src/main/java/ --include="*.java"
```

Both should return empty.

### Step 11: Commit

```bash
git add src/main/java/com/wpanther/xmlsigning/application/dto/
git add src/main/java/com/wpanther/xmlsigning/domain/
git add src/main/java/com/wpanther/xmlsigning/application/service/
git add src/main/java/com/wpanther/xmlsigning/infrastructure/
git commit -m "Move Kafka event DTOs to application/dto/event, move CSC model objects to application/dto/csc"
```

---

## Task 2: Split Domain Ports, Extract XadesEmbeddingPort, Merge into application/usecase/

**Design doc ref:** Phase 2 — the big restructuring: `SignedXmlDocumentRepository` → `domain/repository/`; non-repository ports → `application/port/out/`; new `XadesEmbeddingPort` extracted; `domain/port/in/` + `domain/service/{XmlSigningService,SigningResult}` + `application/service/*` → `application/usecase/`.

**New file to create:**
- `src/main/java/com/wpanther/xmlsigning/application/port/out/XadesEmbeddingPort.java`

**Files to move:**
- `domain/port/out/SignedXmlDocumentRepository.java` → `domain/repository/`
- `domain/port/out/{CscAuthorizationPort, CscSignaturePort, XmlStoragePort, XmlSignedEventPort, SagaReplyPort}.java` → `application/port/out/`
- `domain/port/in/SagaCommandPort.java` → `application/usecase/`
- `domain/service/XmlSigningService.java` → `application/usecase/`
- `domain/service/SigningResult.java` → `application/usecase/`
- `application/service/SagaCommandHandler.java` → `application/usecase/`
- `application/service/XmlSigningServiceImpl.java` → `application/usecase/` (+ add XadesEmbeddingPort injection)

**Files to delete after moves:**
- `src/main/java/com/wpanther/xmlsigning/domain/port/` (entire directory)
- `src/main/java/com/wpanther/xmlsigning/application/service/` (entire directory)
- `src/main/java/com/wpanther/xmlsigning/domain/service/XmlSigningService.java`
- `src/main/java/com/wpanther/xmlsigning/domain/service/SigningResult.java`

**Consumers that need import updates:**
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/storage/MinioXmlStorageAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/persistence/SignedXmlDocumentRepositoryAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/embedder/XadesSignatureEmbedder.java` (add implements)

### Step 1: Create new package directories

```bash
mkdir -p src/main/java/com/wpanther/xmlsigning/domain/repository
mkdir -p src/main/java/com/wpanther/xmlsigning/application/port/out
mkdir -p src/main/java/com/wpanther/xmlsigning/application/usecase
```

### Step 2: Create `XadesEmbeddingPort`

Create `src/main/java/com/wpanther/xmlsigning/application/port/out/XadesEmbeddingPort.java`:

```java
package com.wpanther.xmlsigning.application.port.out;

public interface XadesEmbeddingPort {
    byte[] embedSignature(byte[] xmlContent, byte[] signatureBytes,
                          String certificate, String documentId);
}
```

### Step 3: Move `SignedXmlDocumentRepository` → `domain/repository/`

Copy `domain/port/out/SignedXmlDocumentRepository.java` to `domain/repository/SignedXmlDocumentRepository.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.domain.port.out;

// New:
package com.wpanther.xmlsigning.domain.repository;
```

### Step 4: Move five non-repository ports → `application/port/out/`

For each of `CscAuthorizationPort.java`, `CscSignaturePort.java`, `XmlStoragePort.java`, `XmlSignedEventPort.java`, `SagaReplyPort.java`:

Copy from `domain/port/out/` to `application/port/out/` and change the package declaration:

```java
// Old:
package com.wpanther.xmlsigning.domain.port.out;

// New:
package com.wpanther.xmlsigning.application.port.out;
```

Each of these files may also import `application.dto.csc.*` (already updated in Task 1) — verify those imports are already correct.

### Step 5: Move `SagaCommandPort` → `application/usecase/`

Copy `domain/port/in/SagaCommandPort.java` to `application/usecase/SagaCommandPort.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.domain.port.in;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

Update any imports inside `SagaCommandPort.java` referencing `domain.dto.event.*` → confirm they're already `application.dto.event.*` (done in Task 1).

### Step 6: Move `XmlSigningService` + `SigningResult` → `application/usecase/`

Copy `domain/service/XmlSigningService.java` to `application/usecase/XmlSigningService.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.domain.service;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

Copy `domain/service/SigningResult.java` to `application/usecase/SigningResult.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.domain.service;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

### Step 7: Move `SagaCommandHandler` → `application/usecase/`

Copy `application/service/SagaCommandHandler.java` to `application/usecase/SagaCommandHandler.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.application.service;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

Update imports inside `SagaCommandHandler.java`:

```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.SignedXmlDocumentRepository;
// With:
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;

// Replace all:
import com.wpanther.xmlsigning.domain.port.out.
// With:
import com.wpanther.xmlsigning.application.port.out.

// Replace:
import com.wpanther.xmlsigning.domain.port.in.SagaCommandPort;
// With: (same package — remove this import entirely)

// Replace:
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
// With: (same package — remove this import entirely)

// Replace:
import com.wpanther.xmlsigning.application.service.XmlSigningServiceImpl;
// With: (same package — remove if directly referenced by class name, otherwise leave)
```

### Step 8: Move `XmlSigningServiceImpl` → `application/usecase/` and inject `XadesEmbeddingPort`

Copy `application/service/XmlSigningServiceImpl.java` to `application/usecase/XmlSigningServiceImpl.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.application.service;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

Update imports:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.CscAuthorizationPort;
import com.wpanther.xmlsigning.domain.port.out.CscSignaturePort;
// With:
import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;

// Replace:
import com.wpanther.xmlsigning.domain.service.XmlSigningService;
import com.wpanther.xmlsigning.domain.service.SigningResult;
// With: (same package after move — remove these imports)

// Remove the direct XadesSignatureEmbedder import:
// import com.wpanther.xmlsigning.infrastructure.embedder.XadesSignatureEmbedder;
// (replaced by XadesEmbeddingPort)
```

Replace the `XadesSignatureEmbedder` field with `XadesEmbeddingPort`:

```java
// Old field:
private final XadesSignatureEmbedder xadesSignatureEmbedder;

// New field:
private final XadesEmbeddingPort xadesEmbeddingPort;
```

Update the constructor to inject `XadesEmbeddingPort` instead of `XadesSignatureEmbedder`:

```java
// Old constructor parameter: XadesSignatureEmbedder xadesSignatureEmbedder
// New constructor parameter: XadesEmbeddingPort xadesEmbeddingPort
// Update the field assignment accordingly
```

Update any call sites inside `XmlSigningServiceImpl` that call `xadesSignatureEmbedder.*`:
```java
// Old: xadesSignatureEmbedder.embedSignature(xmlContent, signatureBytes, certificate, documentId)
// New: xadesEmbeddingPort.embedSignature(xmlContent, signatureBytes, certificate, documentId)
```

### Step 9: Add `implements XadesEmbeddingPort` to `XadesSignatureEmbedder`

In `src/main/java/com/wpanther/xmlsigning/infrastructure/embedder/XadesSignatureEmbedder.java`:

```java
// Add import:
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;

// Change class declaration from:
public class XadesSignatureEmbedder {
// To:
public class XadesSignatureEmbedder implements XadesEmbeddingPort {
```

Ensure the `embedSignature` method signature matches the port interface exactly:
```java
@Override
public byte[] embedSignature(byte[] xmlContent, byte[] signatureBytes,
                              String certificate, String documentId) {
    // existing implementation unchanged
}
```

### Step 10: Delete old source directories

```bash
rm -rf src/main/java/com/wpanther/xmlsigning/domain/port
rm src/main/java/com/wpanther/xmlsigning/domain/service/XmlSigningService.java
rm src/main/java/com/wpanther/xmlsigning/domain/service/SigningResult.java
rm -rf src/main/java/com/wpanther/xmlsigning/application/service
```

### Step 11: Update `SagaRouteConfig.java` imports

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`:

```java
// Replace:
import com.wpanther.xmlsigning.domain.port.in.SagaCommandPort;
// With:
import com.wpanther.xmlsigning.application.usecase.SagaCommandPort;
```

### Step 12: Update CSC adapter imports

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.CscAuthorizationPort;
// With:
import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
```

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.CscSignaturePort;
// With:
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
```

### Step 13: Update storage adapter import

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/storage/MinioXmlStorageAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.XmlStoragePort;
// With:
import com.wpanther.xmlsigning.application.port.out.XmlStoragePort;
```

### Step 14: Update messaging adapter imports

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.SagaReplyPort;
// With:
import com.wpanther.xmlsigning.application.port.out.SagaReplyPort;
```

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/messaging/OutboxXmlSignedEventAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.XmlSignedEventPort;
// With:
import com.wpanther.xmlsigning.application.port.out.XmlSignedEventPort;
```

### Step 15: Update persistence adapter import

In `src/main/java/com/wpanther/xmlsigning/infrastructure/persistence/SignedXmlDocumentRepositoryAdapter.java`:
```java
// Replace:
import com.wpanther.xmlsigning.domain.port.out.SignedXmlDocumentRepository;
// With:
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
```

### Step 16: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

Sanity checks:
```bash
grep -r "domain\.port\." src/main/java/ --include="*.java"
grep -r "domain\.service\.XmlSigningService\|domain\.service\.SigningResult" src/main/java/ --include="*.java"
grep -r "application\.service\." src/main/java/ --include="*.java"
grep -r "infrastructure\.embedder\." src/main/java/ --include="*.java"
```

All should return empty.

### Step 17: Commit

```bash
git add src/main/java/com/wpanther/xmlsigning/
git commit -m "Split domain ports, extract XadesEmbeddingPort, merge into application/usecase"
```

---

## Task 3: Consolidate Infrastructure Packages and Delete Legacy

**Design doc ref:** Phase 3 — `infrastructure/client/csc/` → `adapter/out/csc/`; `CommandValidator` → `adapter/in/camel/`; `XadesSignatureEmbedder` + `SecureXmlParser` → `adapter/out/xml/`; delete `MinioStorageService`.

**Files to move:**

*`infrastructure/client/csc/` → `infrastructure/adapter/out/csc/client/`:*
- `CSCAuthClient.java`
- `CSCSignatureClient.java`

*`infrastructure/client/csc/dto/` → `infrastructure/adapter/out/csc/dto/`:*
- `CSCAuthorizeRequest.java`
- `CSCAuthorizeResponse.java`
- `CSCSignatureRequest.java`
- `CSCSignatureResponse.java`
- `SignatureAttributes.java`
- `SignatureData.java`

*`infrastructure/messaging/` → `infrastructure/adapter/in/camel/`:*
- `CommandValidator.java`

*`infrastructure/embedder/` + `infrastructure/util/` → `infrastructure/adapter/out/xml/`:*
- `XadesSignatureEmbedder.java`
- `SecureXmlParser.java`

**Files to delete:**
- `src/main/java/com/wpanther/xmlsigning/infrastructure/storage/MinioStorageService.java`

**Files that need import updates:**
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/config/FeignConfig.java`
- `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java` (CommandValidator — may become same-package, remove import)

### Step 1: Create new package directories

```bash
mkdir -p src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/client
mkdir -p src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/dto
mkdir -p src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/xml
```

### Step 2: Move Feign client files → `adapter/out/csc/client/`

For each of `CSCAuthClient.java`, `CSCSignatureClient.java`:

Copy from `infrastructure/client/csc/` to `infrastructure/adapter/out/csc/client/` and change the package declaration:

```java
// Old:
package com.wpanther.xmlsigning.infrastructure.client.csc;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc.client;
```

### Step 3: Move CSC DTO files → `adapter/out/csc/dto/`

For each of the six DTO files (`CSCAuthorizeRequest`, `CSCAuthorizeResponse`, `CSCSignatureRequest`, `CSCSignatureResponse`, `SignatureAttributes`, `SignatureData`):

Copy from `infrastructure/client/csc/dto/` to `infrastructure/adapter/out/csc/dto/` and change the package declaration:

```java
// Old:
package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto;
```

### Step 4: Delete the old client directory

```bash
rm -rf src/main/java/com/wpanther/xmlsigning/infrastructure/client
```

### Step 5: Move `CommandValidator` → `adapter/in/camel/`

Copy `infrastructure/messaging/CommandValidator.java` to `infrastructure/adapter/in/camel/CommandValidator.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.messaging;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.in.camel;
```

Delete the old messaging directory:
```bash
rm -rf src/main/java/com/wpanther/xmlsigning/infrastructure/messaging
```

### Step 6: Move `XadesSignatureEmbedder` → `adapter/out/xml/`

Copy `infrastructure/embedder/XadesSignatureEmbedder.java` to `infrastructure/adapter/out/xml/XadesSignatureEmbedder.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.embedder;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.out.xml;
```

The `implements XadesEmbeddingPort` declaration added in Task 2 remains. The import for `XadesEmbeddingPort` stays:
```java
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
```

Delete the old embedder directory:
```bash
rm -rf src/main/java/com/wpanther/xmlsigning/infrastructure/embedder
```

### Step 7: Move `SecureXmlParser` → `adapter/out/xml/`

Copy `infrastructure/util/SecureXmlParser.java` to `infrastructure/adapter/out/xml/SecureXmlParser.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.util;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.out.xml;
```

Since `XadesSignatureEmbedder` and `SecureXmlParser` are now in the same package, any import of `SecureXmlParser` inside `XadesSignatureEmbedder` should be **removed** (same-package classes need no import in Java).

Delete the old util directory:
```bash
rm -rf src/main/java/com/wpanther/xmlsigning/infrastructure/util
```

### Step 8: Delete legacy `MinioStorageService`

```bash
rm src/main/java/com/wpanther/xmlsigning/infrastructure/storage/MinioStorageService.java
rmdir src/main/java/com/wpanther/xmlsigning/infrastructure/storage 2>/dev/null || true
```

If `infrastructure/storage/` contains other files, delete only `MinioStorageService.java` and leave the rest.

### Step 9: Update `CscAuthorizationAdapter.java` imports

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscAuthorizationAdapter.java`:

```java
// Replace:
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
// (any other infrastructure.client.csc imports)

// With:
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.client.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeRequest;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeResponse;
```

### Step 10: Update `CscSignatureAdapter.java` imports

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/CscSignatureAdapter.java`:

```java
// Replace all infrastructure.client.csc imports with adapter equivalents:
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.CSCSignatureResponse;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.SignatureAttributes;
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.SignatureData;

// New:
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.client.CSCSignatureClient;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.CSCSignatureRequest;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.CSCSignatureResponse;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.SignatureAttributes;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.SignatureData;
```

### Step 11: Update `FeignConfig.java` imports

In `src/main/java/com/wpanther/xmlsigning/infrastructure/config/FeignConfig.java`:

```java
// Replace:
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.client.csc.CSCSignatureClient;

// With:
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.client.CSCAuthClient;
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.client.CSCSignatureClient;
```

### Step 12: Update `SagaRouteConfig.java` — CommandValidator import

In `src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`:

```java
// Remove (now same package — import no longer needed):
import com.wpanther.xmlsigning.infrastructure.messaging.CommandValidator;
```

If `SagaRouteConfig` is in `infrastructure.adapter.in.camel` and `CommandValidator` is now also in `infrastructure.adapter.in.camel`, the import statement is unnecessary and should be deleted.

### Step 13: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

Sanity checks:
```bash
grep -r "infrastructure\.client\." src/main/java/ --include="*.java"
grep -r "infrastructure\.embedder\." src/main/java/ --include="*.java"
grep -r "infrastructure\.util\." src/main/java/ --include="*.java"
grep -r "infrastructure\.messaging\." src/main/java/ --include="*.java"
grep -r "infrastructure\.storage\.MinioStorageService" src/main/java/ --include="*.java"
```

All should return empty.

### Step 14: Commit

```bash
git add src/main/java/com/wpanther/xmlsigning/infrastructure/
git commit -m "Consolidate infrastructure packages into adapter subdirectories, delete legacy storage"
```

---

## Task 4: Move Config Classes to Concern Sub-Packages

**Design doc ref:** Phase 4 — `FeignConfig` + `CSCErrorDecoder` → `config/feign/`; `MinioConfig` → `config/minio/`; `OutboxConfig` → `config/outbox/`.

**Files to move:**
- `infrastructure/config/FeignConfig.java` → `infrastructure/config/feign/`
- `infrastructure/config/CSCErrorDecoder.java` → `infrastructure/config/feign/`
- `infrastructure/config/MinioConfig.java` → `infrastructure/config/minio/`
- `infrastructure/config/OutboxConfig.java` → `infrastructure/config/outbox/`

**Files that need import updates:** None — Spring `@Configuration` classes are discovered by component scan, not imported by other classes. Verify with:

```bash
grep -r "infrastructure\.config\.FeignConfig\|infrastructure\.config\.CSCErrorDecoder\|infrastructure\.config\.MinioConfig\|infrastructure\.config\.OutboxConfig" src/main/java/ --include="*.java"
```

### Step 1: Create new config sub-directories

```bash
mkdir -p src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign
mkdir -p src/main/java/com/wpanther/xmlsigning/infrastructure/config/minio
mkdir -p src/main/java/com/wpanther/xmlsigning/infrastructure/config/outbox
```

### Step 2: Move `FeignConfig` → `config/feign/`

Copy `infrastructure/config/FeignConfig.java` to `infrastructure/config/feign/FeignConfig.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.config;

// New:
package com.wpanther.xmlsigning.infrastructure.config.feign;
```

Delete the original:
```bash
rm src/main/java/com/wpanther/xmlsigning/infrastructure/config/FeignConfig.java
```

### Step 3: Move `CSCErrorDecoder` → `config/feign/`

Copy `infrastructure/config/CSCErrorDecoder.java` to `infrastructure/config/feign/CSCErrorDecoder.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.config;

// New:
package com.wpanther.xmlsigning.infrastructure.config.feign;
```

Delete the original:
```bash
rm src/main/java/com/wpanther/xmlsigning/infrastructure/config/CSCErrorDecoder.java
```

If `CSCErrorDecoder` is referenced in `FeignConfig` (both now in same package), remove the import between them.

### Step 4: Move `MinioConfig` → `config/minio/`

Copy `infrastructure/config/MinioConfig.java` to `infrastructure/config/minio/MinioConfig.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.config;

// New:
package com.wpanther.xmlsigning.infrastructure.config.minio;
```

Delete the original:
```bash
rm src/main/java/com/wpanther/xmlsigning/infrastructure/config/MinioConfig.java
```

### Step 5: Move `OutboxConfig` → `config/outbox/`

Copy `infrastructure/config/OutboxConfig.java` to `infrastructure/config/outbox/OutboxConfig.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.config;

// New:
package com.wpanther.xmlsigning.infrastructure.config.outbox;
```

Delete the original:
```bash
rm src/main/java/com/wpanther/xmlsigning/infrastructure/config/OutboxConfig.java
```

### Step 6: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

### Step 7: Commit

```bash
git add src/main/java/com/wpanther/xmlsigning/infrastructure/config/
git commit -m "Move config classes to concern-based sub-packages"
```

---

## Task 5: Relocate Test Files

**Design doc ref:** Phase 5 — mirror source package moves in test tree; update JaCoCo; delete `MinioStorageServiceTest`.

**Test files to relocate:**

| Old test path | New test path |
|---|---|
| `domain/event/ProcessXmlSigningCommandTest.java` | `application/dto/event/` |
| `domain/event/CompensateXmlSigningCommandTest.java` | `application/dto/event/` |
| `domain/event/XmlSigningReplyEventTest.java` | `application/dto/event/` |
| `domain/event/XmlSignedEventTest.java` | `application/dto/event/` |
| `domain/event/XmlSigningRequestedEventTest.java` | `application/dto/event/` |
| `domain/model/csc/CscDomainValueObjectsTest.java` | `application/dto/csc/` |
| `application/service/SagaCommandHandlerTest.java` | `application/usecase/` |
| `application/service/XmlSigningServiceImplTest.java` | `application/usecase/` |
| `infrastructure/client/csc/dto/CSCDtoTest.java` | `infrastructure/adapter/out/csc/dto/` |
| `infrastructure/config/FeignConfigTest.java` | `infrastructure/config/feign/` |
| `infrastructure/config/CSCErrorDecoderTest.java` | `infrastructure/config/feign/` |
| `infrastructure/config/OutboxConfigTest.java` | `infrastructure/config/outbox/` |
| `infrastructure/embedder/XadesSignatureEmbedderTest.java` | `infrastructure/adapter/out/xml/` |
| `infrastructure/storage/MinioStorageServiceTest.java` | **DELETED** |

**Not moved:** `domain/model/*Test`, `domain/service/DocumentTypeDetectionServiceTest`, `infrastructure/adapter/out/csc/*Test`, `infrastructure/adapter/out/storage/*`, `infrastructure/adapter/out/messaging/*`, `infrastructure/adapter/in/camel/*`, `infrastructure/persistence/*`, `integration/*`, `XmlSigningServiceApplicationTest`.

### Step 1: Create new test directories

```bash
mkdir -p src/test/java/com/wpanther/xmlsigning/application/dto/event
mkdir -p src/test/java/com/wpanther/xmlsigning/application/dto/csc
mkdir -p src/test/java/com/wpanther/xmlsigning/application/usecase
mkdir -p src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/dto
mkdir -p src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/xml
mkdir -p src/test/java/com/wpanther/xmlsigning/infrastructure/config/feign
mkdir -p src/test/java/com/wpanther/xmlsigning/infrastructure/config/outbox
```

### Step 2: Delete `MinioStorageServiceTest`

```bash
rm src/test/java/com/wpanther/xmlsigning/infrastructure/storage/MinioStorageServiceTest.java
rmdir src/test/java/com/wpanther/xmlsigning/infrastructure/storage 2>/dev/null || true
```

### Step 3: Relocate domain/event tests → `application/dto/event/`

For each of the five event test files:

Copy from `src/test/java/com/wpanther/xmlsigning/domain/event/` to `src/test/java/com/wpanther/xmlsigning/application/dto/event/`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.domain.event;

// New:
package com.wpanther.xmlsigning.application.dto.event;
```

Update imports of the tested classes:
```java
// Old:
import com.wpanther.xmlsigning.domain.event.ProcessXmlSigningCommand;
// (and similar for other event classes)

// New:
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;
// (and similar)
```

Delete the old test directory:
```bash
rm -rf src/test/java/com/wpanther/xmlsigning/domain/event
```

### Step 4: Relocate `CscDomainValueObjectsTest` → `application/dto/csc/`

Copy `src/test/java/com/wpanther/xmlsigning/domain/model/csc/CscDomainValueObjectsTest.java` to `src/test/java/com/wpanther/xmlsigning/application/dto/csc/CscDomainValueObjectsTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.domain.model.csc;

// New:
package com.wpanther.xmlsigning.application.dto.csc;
```

Update imports:
```java
// Old:
import com.wpanther.xmlsigning.domain.model.csc.CscAuthorizeCommand;
// etc.

// New:
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
// etc.
```

Delete old test directory:
```bash
rm -rf src/test/java/com/wpanther/xmlsigning/domain/model/csc
```

### Step 5: Relocate `SagaCommandHandlerTest` → `application/usecase/`

Copy `src/test/java/com/wpanther/xmlsigning/application/service/SagaCommandHandlerTest.java` to `src/test/java/com/wpanther/xmlsigning/application/usecase/SagaCommandHandlerTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.application.service;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

Update imports — replace all `application.service.*` and `domain.port.*` and `domain.event.*` references:
```java
import com.wpanther.xmlsigning.application.usecase.SagaCommandHandler;
import com.wpanther.xmlsigning.application.port.out.SagaReplyPort;
import com.wpanther.xmlsigning.application.port.out.XmlSignedEventPort;
import com.wpanther.xmlsigning.application.port.out.XmlStoragePort;
import com.wpanther.xmlsigning.application.dto.event.ProcessXmlSigningCommand;
import com.wpanther.xmlsigning.application.dto.event.CompensateXmlSigningCommand;
import com.wpanther.xmlsigning.domain.repository.SignedXmlDocumentRepository;
// (adjust as needed based on actual imports in the test)
```

### Step 6: Relocate `XmlSigningServiceImplTest` → `application/usecase/`

Copy `src/test/java/com/wpanther/xmlsigning/application/service/XmlSigningServiceImplTest.java` to `src/test/java/com/wpanther/xmlsigning/application/usecase/XmlSigningServiceImplTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.application.service;

// New:
package com.wpanther.xmlsigning.application.usecase;
```

Update the mock for `XadesSignatureEmbedder` → `XadesEmbeddingPort`:
```java
// Old:
@Mock
XadesSignatureEmbedder xadesSignatureEmbedder;

// New:
@Mock
XadesEmbeddingPort xadesEmbeddingPort;
```

Update constructor/injection calls in the test setup to pass `xadesEmbeddingPort` instead of `xadesSignatureEmbedder`.

Update remaining imports:
```java
// Remove:
import com.wpanther.xmlsigning.infrastructure.embedder.XadesSignatureEmbedder;

// Add:
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
import com.wpanther.xmlsigning.application.port.out.CscAuthorizationPort;
import com.wpanther.xmlsigning.application.port.out.CscSignaturePort;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscAuthorizeResult;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashCommand;
import com.wpanther.xmlsigning.application.dto.csc.CscSignHashResult;
```

Delete old test directory:
```bash
rm -rf src/test/java/com/wpanther/xmlsigning/application/service
```

### Step 7: Relocate `CSCDtoTest` → `infrastructure/adapter/out/csc/dto/`

Copy `src/test/java/com/wpanther/xmlsigning/infrastructure/client/csc/dto/CSCDtoTest.java` to `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/dto/CSCDtoTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.client.csc.dto;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto;
```

Update imports:
```java
// Replace all:
import com.wpanther.xmlsigning.infrastructure.client.csc.dto.
// With:
import com.wpanther.xmlsigning.infrastructure.adapter.out.csc.dto.
```

Delete old test directory:
```bash
rm -rf src/test/java/com/wpanther/xmlsigning/infrastructure/client
```

### Step 8: Relocate `XadesSignatureEmbedderTest` → `infrastructure/adapter/out/xml/`

Copy `src/test/java/com/wpanther/xmlsigning/infrastructure/embedder/XadesSignatureEmbedderTest.java` to `src/test/java/com/wpanther/xmlsigning/infrastructure/adapter/out/xml/XadesSignatureEmbedderTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.xmlsigning.infrastructure.embedder;

// New:
package com.wpanther.xmlsigning.infrastructure.adapter.out.xml;
```

Update imports:
```java
// Replace:
import com.wpanther.xmlsigning.infrastructure.embedder.XadesSignatureEmbedder;
// With:
import com.wpanther.xmlsigning.infrastructure.adapter.out.xml.XadesSignatureEmbedder;

// Add:
import com.wpanther.xmlsigning.application.port.out.XadesEmbeddingPort;
```

Add the port implementation assertion:
```java
@Test
void implementsXadesEmbeddingPort() {
    assertThat(embedder).isInstanceOf(XadesEmbeddingPort.class);
}
```

Delete old test directory:
```bash
rm -rf src/test/java/com/wpanther/xmlsigning/infrastructure/embedder
```

### Step 9: Relocate config tests → concern sub-packages

**`FeignConfigTest`:**
Copy `src/test/java/com/wpanther/xmlsigning/infrastructure/config/FeignConfigTest.java` to `src/test/java/com/wpanther/xmlsigning/infrastructure/config/feign/FeignConfigTest.java`.

Change package: `infrastructure.config` → `infrastructure.config.feign`

**`CSCErrorDecoderTest`:**
Copy `src/test/java/com/wpanther/xmlsigning/infrastructure/config/CSCErrorDecoderTest.java` to `src/test/java/com/wpanther/xmlsigning/infrastructure/config/feign/CSCErrorDecoderTest.java`.

Change package: `infrastructure.config` → `infrastructure.config.feign`

**`OutboxConfigTest`:**
Copy `src/test/java/com/wpanther/xmlsigning/infrastructure/config/OutboxConfigTest.java` to `src/test/java/com/wpanther/xmlsigning/infrastructure/config/outbox/OutboxConfigTest.java`.

Change package: `infrastructure.config` → `infrastructure.config.outbox`

Delete moved originals from `infrastructure/config/` (but leave `infrastructure/config/` directory if it still has remaining test files).

### Step 10: Update JaCoCo exclusions in `pom.xml`

Open `pom.xml` and find the JaCoCo `<excludes>` section.

**Remove stale entries** (if present):
```xml
<exclude>com/wpanther/xmlsigning/infrastructure/storage/**</exclude>
<exclude>com/wpanther/xmlsigning/infrastructure/client/**</exclude>
```

**Add new exclusion** for relocated CSC wire DTOs (Lombok-heavy, not unit-testable):
```xml
<exclude>com/wpanther/xmlsigning/infrastructure/adapter/out/csc/dto/**</exclude>
```

**Config exclusions** — `infrastructure/config/**` already covers all sub-packages automatically. No additional entries needed.

### Step 11: Run full coverage verification

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service
mvn verify
```

Expected: `BUILD SUCCESS` with all JaCoCo coverage checks passing (≥ 80% line coverage).

If any test fails due to remaining stale imports, find them:
```bash
grep -r "domain\.port\.\|domain\.event\.\|domain\.model\.csc\.\|application\.service\.\|infrastructure\.client\.\|infrastructure\.embedder\.\|infrastructure\.messaging\.\|infrastructure\.storage\.Minio" src/test/java/ --include="*.java"
```

### Step 12: Commit

```bash
git add src/test/java/com/wpanther/xmlsigning/
git add pom.xml
git commit -m "Relocate test classes, update JaCoCo exclusions"
```

---

## Task 6: Final Verification

**Design doc ref:** Phase 6 — confirm no old package references remain anywhere.

### Step 1: Confirm no old package references in source

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/xml-signing-service

# These should all return empty:
grep -r "domain\.port\." src/ --include="*.java"
grep -r "domain\.event\." src/ --include="*.java"
grep -r "domain\.model\.csc\." src/ --include="*.java"
grep -r "domain\.service\.XmlSigningService\|domain\.service\.SigningResult" src/ --include="*.java"
grep -r "application\.service\." src/ --include="*.java"
grep -r "infrastructure\.client\." src/ --include="*.java"
grep -r "infrastructure\.embedder\." src/ --include="*.java"
grep -r "infrastructure\.util\." src/ --include="*.java"
grep -r "infrastructure\.messaging\." src/ --include="*.java"
grep -r "infrastructure\.storage\.MinioStorageService" src/ --include="*.java"
```

### Step 2: Confirm deleted directories are gone

```bash
# These should all print "No such file or directory":
ls src/main/java/com/wpanther/xmlsigning/domain/port 2>&1
ls src/main/java/com/wpanther/xmlsigning/domain/event 2>&1
ls src/main/java/com/wpanther/xmlsigning/domain/model/csc 2>&1
ls src/main/java/com/wpanther/xmlsigning/application/service 2>&1
ls src/main/java/com/wpanther/xmlsigning/infrastructure/client 2>&1
ls src/main/java/com/wpanther/xmlsigning/infrastructure/embedder 2>&1
ls src/main/java/com/wpanther/xmlsigning/infrastructure/util 2>&1
ls src/main/java/com/wpanther/xmlsigning/infrastructure/messaging 2>&1
```

### Step 3: Confirm new directories exist and are populated

```bash
ls src/main/java/com/wpanther/xmlsigning/domain/repository/
ls src/main/java/com/wpanther/xmlsigning/application/usecase/
ls src/main/java/com/wpanther/xmlsigning/application/port/out/
ls src/main/java/com/wpanther/xmlsigning/application/dto/event/
ls src/main/java/com/wpanther/xmlsigning/application/dto/csc/
ls src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/client/
ls src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/csc/dto/
ls src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/xml/
ls src/main/java/com/wpanther/xmlsigning/infrastructure/config/feign/
ls src/main/java/com/wpanther/xmlsigning/infrastructure/config/minio/
ls src/main/java/com/wpanther/xmlsigning/infrastructure/config/outbox/
```

### Step 4: Confirm `XadesEmbeddingPort` is in place

```bash
# Should return the new port file:
ls src/main/java/com/wpanther/xmlsigning/application/port/out/XadesEmbeddingPort.java

# Should show 'implements XadesEmbeddingPort':
grep "implements XadesEmbeddingPort" src/main/java/com/wpanther/xmlsigning/infrastructure/adapter/out/xml/XadesSignatureEmbedder.java
```

### Step 5: Final full build and coverage check

```bash
mvn verify
```

Expected: `BUILD SUCCESS` — all tests pass, JaCoCo ≥ 80% line coverage maintained.
