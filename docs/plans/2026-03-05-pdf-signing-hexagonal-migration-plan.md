# pdf-signing-service Hexagonal Migration — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate `pdf-signing-service` to strict hexagonal architecture that fully mirrors the
`xml-signing-service` conventions established in the preceding migration.

**Architecture:** Approach B (Full Convention Alignment) — fix all architectural violations
(application layer depending on infra class, inbound adapter bypassing port, repository in wrong
package) AND align all naming conventions (`secondary/` → `out/`, `primary/` → `in/camel/`,
flat `domain/port/` → `domain/port/out/`). Eight incremental tasks, each leaves `mvn test` green.
No database schema changes. No Kafka topic changes.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel, Mockito, AssertJ, Lombok, MapStruct.
Test runner: `cd services/pdf-signing-service && mvn test`.

**Design doc:** `docs/plans/2026-03-05-pdf-signing-hexagonal-migration-design.md`

---

## Context: What Already Exists

The pdf-signing-service is **partially** hexagonal. The following is already correct:
- `SagaCommandPort` in `domain/port/in/` ✅
- `SagaCommandHandler` implements `SagaCommandPort` ✅
- `CscSigningAdapter`, `HttpDocumentDownloadAdapter`, `PadesSignatureAdapter`,
  `LocalStorageAdapter`, `S3StorageAdapter` implement domain port interfaces ✅
- Outbox pattern via `OutboxService` ✅

**Remaining violations:**
- `DocumentDownloadPort`, `DocumentStoragePort`, `PdfGenerationPort`, `SigningPort`
  are in `domain.port` (flat) — must be `domain.port.out`
- `SignedPdfDocumentRepository` is in `domain.repository` — must be `domain.port.out`
- `SagaCommandHandler` injects `PdfSigningEventPublisher` (concrete infra class)
- `SagaRouteConfig` in `infrastructure.config` calls `SagaCommandHandler` directly
- `adapter/primary/` and `adapter/secondary/` naming must become `adapter/in/` and `adapter/out/`
- `infrastructure/messaging/` publishers must become adapters behind port interfaces

**Run after every task:**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

---

# Task 1: Move flat `domain/port/*.java` → `domain/port/out/`

**Why:** `DocumentDownloadPort`, `DocumentStoragePort`, `PdfGenerationPort`, and `SigningPort`
are outbound ports (the domain drives out through them) — they must live in `domain.port.out`
to match hexagonal conventions. This is a package rename only; no logic changes.

**Files:**
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/port/DocumentDownloadPort.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/port/DocumentStoragePort.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/port/PdfGenerationPort.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/port/SigningPort.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/service/DomainPdfSigningService.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/secondary/csc/CscSigningAdapter.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/secondary/download/HttpDocumentDownloadAdapter.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/secondary/pdf/PadesSignatureAdapter.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/secondary/storage/LocalStorageAdapter.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/secondary/storage/S3StorageAdapter.java`

**Step 1: Move the four port files to the new `out/` subpackage**

Create `src/main/java/com/wpanther/pdfsigning/domain/port/out/` directory (Java creates it
automatically when you update the package declaration).

Change the `package` declaration at the top of each file:

`DocumentDownloadPort.java`:
```java
// Before:
package com.wpanther.pdfsigning.domain.port;
// After:
package com.wpanther.pdfsigning.domain.port.out;
```

`DocumentStoragePort.java`:
```java
// Before:
package com.wpanther.pdfsigning.domain.port;
// After:
package com.wpanther.pdfsigning.domain.port.out;
```

`PdfGenerationPort.java`:
```java
// Before:
package com.wpanther.pdfsigning.domain.port;
// After:
package com.wpanther.pdfsigning.domain.port.out;
```

`SigningPort.java`:
```java
// Before:
package com.wpanther.pdfsigning.domain.port;
// After:
package com.wpanther.pdfsigning.domain.port.out;
```

Also move the physical files to the new directory:
```bash
mkdir -p services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out
mv services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/DocumentDownloadPort.java \
   services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out/
mv services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/DocumentStoragePort.java \
   services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out/
mv services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/PdfGenerationPort.java \
   services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out/
mv services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/SigningPort.java \
   services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out/
```

**Step 2: Update imports in `DomainPdfSigningService.java`**

```java
// Before:
import com.wpanther.pdfsigning.domain.port.DocumentDownloadPort;
import com.wpanther.pdfsigning.domain.port.DocumentStoragePort;
import com.wpanther.pdfsigning.domain.port.PdfGenerationPort;
import com.wpanther.pdfsigning.domain.port.SigningPort;

// After:
import com.wpanther.pdfsigning.domain.port.out.DocumentDownloadPort;
import com.wpanther.pdfsigning.domain.port.out.DocumentStoragePort;
import com.wpanther.pdfsigning.domain.port.out.PdfGenerationPort;
import com.wpanther.pdfsigning.domain.port.out.SigningPort;
```

**Step 3: Update imports in all five adapter files**

`CscSigningAdapter.java`:
```java
// Before:
import com.wpanther.pdfsigning.domain.port.SigningPort;
// After:
import com.wpanther.pdfsigning.domain.port.out.SigningPort;
```

`HttpDocumentDownloadAdapter.java`:
```java
// Before:
import com.wpanther.pdfsigning.domain.port.DocumentDownloadPort;
// After:
import com.wpanther.pdfsigning.domain.port.out.DocumentDownloadPort;
```

`PadesSignatureAdapter.java`:
```java
// Before:
import com.wpanther.pdfsigning.domain.port.PdfGenerationPort;
// After:
import com.wpanther.pdfsigning.domain.port.out.PdfGenerationPort;
```

`LocalStorageAdapter.java`:
```java
// Before:
import com.wpanther.pdfsigning.domain.port.DocumentStoragePort;
// After:
import com.wpanther.pdfsigning.domain.port.out.DocumentStoragePort;
```

`S3StorageAdapter.java`:
```java
// Before:
import com.wpanther.pdfsigning.domain.port.DocumentStoragePort;
// After:
import com.wpanther.pdfsigning.domain.port.out.DocumentStoragePort;
```

**Step 4: Update test imports**

In every test file under `src/test/java/` that imports from `domain.port`, apply the same
import change (replace `domain.port.X` → `domain.port.out.X`). Run a search first:
```bash
grep -r "domain\.port\." services/pdf-signing-service/src/test --include="*.java" -l
```
Update each file found.

**Step 5: Verify compilation**
```bash
cd services/pdf-signing-service && mvn compile
```
Expected: BUILD SUCCESS. Fix any remaining `cannot find symbol` errors for the old package.

**Step 6: Run tests**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

**Step 7: Commit**
```bash
cd services/pdf-signing-service
git add src/main/java/com/wpanther/pdfsigning/domain/port/out/ \
        src/main/java/com/wpanther/pdfsigning/domain/service/DomainPdfSigningService.java \
        src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/secondary/
git commit -m "refactor: move outbound port interfaces to domain/port/out/"
```

---

# Task 2: Move `SignedPdfDocumentRepository` → `domain/port/out/`; delete `domain/repository/`

**Why:** Repository interfaces are outbound ports — the domain calls out through them to
persistence. They belong in `domain.port.out`, not a separate `domain.repository` package.

**Files:**
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/repository/SignedPdfDocumentRepository.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/domain/service/DomainPdfSigningService.java`
- Modify: `src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/SignedPdfDocumentRepositoryAdapter.java`
- Modify: `src/test/java/com/wpanther/pdfsigning/application/service/SagaCommandHandlerTest.java`

**Step 1: Move and update `SignedPdfDocumentRepository.java`**

Move the file:
```bash
mv services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/repository/SignedPdfDocumentRepository.java \
   services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/port/out/
rmdir services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/domain/repository
```

Change package declaration in `SignedPdfDocumentRepository.java`:
```java
// Before:
package com.wpanther.pdfsigning.domain.repository;
// After:
package com.wpanther.pdfsigning.domain.port.out;
```

**Step 2: Update imports in `SagaCommandHandler.java`**
```java
// Before:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
// After:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;
```

**Step 3: Update imports in `DomainPdfSigningService.java`**
```java
// Before:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
// After:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;
```

**Step 4: Update imports in `SignedPdfDocumentRepositoryAdapter.java`**
```java
// Before:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
// After:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;
```

**Step 5: Update test imports in `SagaCommandHandlerTest.java`**
```java
// Before:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
// After:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;
```

**Step 6: Check for any remaining references to `domain.repository`**
```bash
grep -r "domain\.repository" services/pdf-signing-service/src --include="*.java"
```
Expected: no output.

**Step 7: Run tests**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

**Step 8: Commit**
```bash
cd services/pdf-signing-service
git add src/main/java/com/wpanther/pdfsigning/domain/port/out/SignedPdfDocumentRepository.java \
        src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java \
        src/main/java/com/wpanther/pdfsigning/domain/service/DomainPdfSigningService.java \
        src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/SignedPdfDocumentRepositoryAdapter.java \
        src/test/java/com/wpanther/pdfsigning/application/service/SagaCommandHandlerTest.java
git commit -m "refactor: move SignedPdfDocumentRepository to domain/port/out/"
```

---

# Task 3: Create `PdfSignedEventPort` and `PdfSagaReplyPort`

**Why:** `SagaCommandHandler` currently depends on `PdfSigningEventPublisher` (a concrete
infrastructure class). We need domain port interfaces so the application layer only depends on
abstractions. These are pure interface additions — nothing breaks yet.

**Files:**
- Create: `src/main/java/com/wpanther/pdfsigning/domain/port/out/PdfSignedEventPort.java`
- Create: `src/main/java/com/wpanther/pdfsigning/domain/port/out/PdfSagaReplyPort.java`

**Step 1: Create `PdfSignedEventPort.java`**

```java
package com.wpanther.pdfsigning.domain.port.out;

import java.time.Instant;

/**
 * Outbound port for publishing PDF signing notification events.
 * Consumed by notification-service (not part of saga coordination).
 */
public interface PdfSignedEventPort {

    void publishPdfSignedNotification(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId);

    void publishPdfSigningFailureNotification(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId);
}
```

**Step 2: Create `PdfSagaReplyPort.java`**

```java
package com.wpanther.pdfsigning.domain.port.out;

import com.wpanther.saga.domain.enums.SagaStep;

import java.time.Instant;

/**
 * Outbound port for publishing saga reply events to the orchestrator.
 * Replies are sent via outbox pattern to saga.reply.pdf-signing topic.
 */
public interface PdfSagaReplyPort {

    void publishSuccess(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            Instant signatureTimestamp);

    void publishFailure(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String errorMessage);

    void publishCompensated(
            String sagaId,
            SagaStep sagaStep,
            String correlationId);
}
```

**Step 3: Compile to confirm no errors**
```bash
cd services/pdf-signing-service && mvn compile
```
Expected: BUILD SUCCESS

**Step 4: Run tests (should still pass — nothing wired yet)**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

**Step 5: Commit**
```bash
cd services/pdf-signing-service
git add src/main/java/com/wpanther/pdfsigning/domain/port/out/PdfSignedEventPort.java \
        src/main/java/com/wpanther/pdfsigning/domain/port/out/PdfSagaReplyPort.java
git commit -m "feat: add PdfSignedEventPort and PdfSagaReplyPort outbound port interfaces"
```

---

# Task 4: Create `OutboxPdfSignedEventAdapter` and `OutboxSagaReplyAdapter`

**Why:** Implement the two port interfaces from Task 3. These adapters absorb the logic of
`NotificationEventPublisher` and `SagaReplyPublisher` respectively. After this task both
classes are fully covered by tests; the old publishers are still alive (deleted in Task 5).

**Files:**
- Create: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java`
- Create: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`
- Create: `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapterTest.java`
- Create: `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapterTest.java`

**Step 1: Write the failing test for `OutboxPdfSignedEventAdapter`**

```java
package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPdfSignedEventAdapter Tests")
class OutboxPdfSignedEventAdapterTest {

    @Mock private OutboxService outboxService;
    @Mock private KafkaProperties kafkaProperties;
    private OutboxPdfSignedEventAdapter adapter;

    @BeforeEach
    void setUp() {
        KafkaProperties.Topics topics = new KafkaProperties.Topics();
        topics.setNotificationEvents("notification.events");
        when(kafkaProperties.getTopics()).thenReturn(topics);
        adapter = new OutboxPdfSignedEventAdapter(outboxService, new ObjectMapper(), kafkaProperties);
    }

    @Test
    @DisplayName("publishPdfSignedNotification routes to notification.events topic")
    void publishPdfSignedNotification_routesToNotificationTopic() {
        adapter.publishPdfSignedNotification(
            "saga-1", "inv-1", "INV-001", "TAX_INVOICE",
            "doc-1", "http://example.com/signed.pdf", 12345L,
            "PAdES-BASELINE-B", Instant.now(), "corr-1"
        );

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("notification.events"), eq("inv-1"), any()
        );
    }

    @Test
    @DisplayName("publishPdfSigningFailureNotification routes to notification.events topic")
    void publishPdfSigningFailureNotification_routesToNotificationTopic() {
        adapter.publishPdfSigningFailureNotification(
            "saga-1", "inv-1", "INV-001", "TAX_INVOICE",
            "Signing failed", "corr-1"
        );

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("notification.events"), eq("inv-1"), any()
        );
    }
}
```

**Step 2: Write the failing test for `OutboxSagaReplyAdapter`**

```java
package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxSagaReplyAdapter Tests")
class OutboxSagaReplyAdapterTest {

    @Mock private OutboxService outboxService;
    @Mock private KafkaProperties kafkaProperties;
    private OutboxSagaReplyAdapter adapter;

    @BeforeEach
    void setUp() {
        KafkaProperties.Topics topics = new KafkaProperties.Topics();
        topics.setSagaReply("saga.reply.pdf-signing");
        when(kafkaProperties.getTopics()).thenReturn(topics);
        adapter = new OutboxSagaReplyAdapter(outboxService, new ObjectMapper(), kafkaProperties);
    }

    @Test
    @DisplayName("publishSuccess routes to saga.reply topic")
    void publishSuccess_routesToSagaReplyTopic() {
        adapter.publishSuccess(
            "saga-1", SagaStep.SIGN_PDF, "corr-1",
            "doc-1", "http://example.com/signed.pdf", 12345L,
            "txn-1", "PEM-CERT", "PAdES-BASELINE-B", Instant.now()
        );

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("saga.reply.pdf-signing"), eq("saga-1"), any()
        );
    }

    @Test
    @DisplayName("publishFailure routes to saga.reply topic")
    void publishFailure_routesToSagaReplyTopic() {
        adapter.publishFailure("saga-1", SagaStep.SIGN_PDF, "corr-1", "error msg");

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("saga.reply.pdf-signing"), eq("saga-1"), any()
        );
    }

    @Test
    @DisplayName("publishCompensated routes to saga.reply topic")
    void publishCompensated_routesToSagaReplyTopic() {
        adapter.publishCompensated("saga-1", SagaStep.SIGN_PDF, "corr-1");

        verify(outboxService).saveWithRouting(
            any(), eq("SignedPdfDocument"), any(),
            eq("saga.reply.pdf-signing"), eq("saga-1"), any()
        );
    }
}
```

**Step 3: Run tests to verify they fail**
```bash
cd services/pdf-signing-service && mvn test \
  -Dtest="OutboxPdfSignedEventAdapterTest,OutboxSagaReplyAdapterTest"
```
Expected: FAIL — classes not found.

**Step 4: Create `OutboxPdfSignedEventAdapter.java`**

Body is taken verbatim from `NotificationEventPublisher` — only class name, package, and
implemented interface change:

```java
package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSignedNotificationEvent;
import com.wpanther.pdfsigning.domain.event.PdfSigningFailedNotificationEvent;
import com.wpanther.pdfsigning.domain.port.out.PdfSignedEventPort;
import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPdfSignedEventAdapter implements PdfSignedEventPort {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final KafkaProperties kafkaProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfSignedNotification(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId) {

        PdfSignedNotificationEvent notification = PdfSignedNotificationEvent.create(
            sagaId, invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", "PdfSigned");
        headers.put("documentType", documentType);
        headers.put("correlationId", correlationId);
        headers.put("invoiceId", invoiceId);

        outboxService.saveWithRouting(
            notification,
            "SignedPdfDocument",
            signedDocumentId,
            kafkaProperties.getTopics().getNotificationEvents(),
            invoiceId,
            toJson(headers)
        );

        log.info("Published PdfSigned notification for invoiceId={}, invoiceNumber={}, documentType={}",
            invoiceId, invoiceNumber, documentType);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfSigningFailureNotification(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        PdfSigningFailedNotificationEvent notification = PdfSigningFailedNotificationEvent.create(
            sagaId, invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", "PdfSigningFailed");
        headers.put("documentType", documentType);
        headers.put("correlationId", correlationId);
        headers.put("invoiceId", invoiceId);

        try {
            outboxService.saveWithRouting(
                notification,
                "SignedPdfDocument",
                invoiceId,
                kafkaProperties.getTopics().getNotificationEvents(),
                invoiceId,
                toJson(headers)
            );
            log.warn("Published PdfSigningFailed notification for invoiceId={}, error={}", invoiceId, errorMessage);
        } catch (Exception e) {
            log.error("Failed to publish failure notification for invoiceId={}", invoiceId, e);
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize headers to JSON", e);
            return "{}";
        }
    }
}
```

**Step 5: Create `OutboxSagaReplyAdapter.java`**

Body taken verbatim from `SagaReplyPublisher`:

```java
package com.wpanther.pdfsigning.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSigningReplyEvent;
import com.wpanther.pdfsigning.domain.port.out.PdfSagaReplyPort;
import com.wpanther.pdfsigning.infrastructure.config.properties.KafkaProperties;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxSagaReplyAdapter implements PdfSagaReplyPort {

    private static final String AGGREGATE_TYPE = "SignedPdfDocument";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final KafkaProperties kafkaProperties;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(
            String sagaId,
            SagaStep sagaStep,
            String correlationId,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            Instant signatureTimestamp) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.success(
            sagaId, sagaStep, correlationId,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "SUCCESS");

        outboxService.saveWithRouting(
            reply, AGGREGATE_TYPE, sagaId,
            kafkaProperties.getTopics().getSagaReply(),
            sagaId, toJson(headers)
        );

        log.info("Published SUCCESS saga reply for sagaId={}, correlationId={}", sagaId, correlationId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "FAILURE");

        outboxService.saveWithRouting(
            reply, AGGREGATE_TYPE, sagaId,
            kafkaProperties.getTopics().getSagaReply(),
            sagaId, toJson(headers)
        );

        log.warn("Published FAILURE saga reply for sagaId={}, correlationId={}, error={}",
            sagaId, correlationId, errorMessage);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "COMPENSATED");

        outboxService.saveWithRouting(
            reply, AGGREGATE_TYPE, sagaId,
            kafkaProperties.getTopics().getSagaReply(),
            sagaId, toJson(headers)
        );

        log.info("Published COMPENSATED saga reply for sagaId={}, correlationId={}", sagaId, correlationId);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize headers to JSON", e);
            return "{}";
        }
    }
}
```

**Step 6: Run the new adapter tests**
```bash
cd services/pdf-signing-service && mvn test \
  -Dtest="OutboxPdfSignedEventAdapterTest,OutboxSagaReplyAdapterTest"
```
Expected: PASS

**Step 7: Run all tests**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

**Step 8: Commit**
```bash
cd services/pdf-signing-service
git add src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/ \
        src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/
git commit -m "feat: add OutboxPdfSignedEventAdapter and OutboxSagaReplyAdapter"
```

---

# Task 5: Update `SagaCommandHandler`; delete three publisher classes

**Why:** Wire `SagaCommandHandler` to the new port interfaces. Then delete
`PdfSigningEventPublisher`, `SagaReplyPublisher`, and `NotificationEventPublisher` since their
logic now lives in the adapters. Update `SagaCommandHandlerTest` to mock the ports instead.

**Files:**
- Modify: `src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java`
- Delete: `src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/PdfSigningEventPublisher.java`
- Delete: `src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/SagaReplyPublisher.java`
- Delete: `src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/NotificationEventPublisher.java`
- Delete: `src/test/java/com/wpanther/pdfsigning/infrastructure/messaging/PdfSigningEventPublisherTest.java`
- Delete: `src/test/java/com/wpanther/pdfsigning/infrastructure/messaging/SagaReplyPublisherTest.java`
- Delete: `src/test/java/com/wpanther/pdfsigning/infrastructure/messaging/NotificationEventPublisherTest.java`
- Modify: `src/test/java/com/wpanther/pdfsigning/application/service/SagaCommandHandlerTest.java`

**Step 1: Update `SagaCommandHandler.java`**

Replace the import and field for `PdfSigningEventPublisher` with the two port interfaces.

Change imports (remove old, add new):
```java
// Remove:
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;

// Add:
import com.wpanther.pdfsigning.domain.port.out.PdfSignedEventPort;
import com.wpanther.pdfsigning.domain.port.out.PdfSagaReplyPort;
```

Change the field declaration:
```java
// Before:
private final PdfSigningEventPublisher eventPublisher;

// After:
private final PdfSagaReplyPort sagaReplyPort;
private final PdfSignedEventPort pdfSignedEventPort;
```

Replace every `eventPublisher.publishSuccess(...)` call. There are two sites (happy path and
idempotent path). Replace each with two sequential calls:

```java
// Before (both sites):
eventPublisher.publishSuccess(
    command.getSagaId(),
    command.getSagaStep(),
    command.getCorrelationId(),
    command.getDocumentId(),
    command.getInvoiceNumber(),
    command.getDocumentType(),
    document.getId().toString(),
    result.signedPdfUrl(),   // or document.getSignedPdfUrl() for idempotent path
    result.signedPdfSize(),  // or document.getSignedPdfSize()
    result.transactionId(),  // or document.getTransactionId()
    result.certificate(),    // or document.getCertificate()
    result.signatureLevel(), // or document.getSignatureLevel()
    result.signatureTimestamp() // or document.getSignatureTimestamp()...
);

// After (happy path — result variables):
sagaReplyPort.publishSuccess(
    command.getSagaId(),
    command.getSagaStep(),
    command.getCorrelationId(),
    document.getId().toString(),
    result.signedPdfUrl(),
    result.signedPdfSize(),
    result.transactionId(),
    result.certificate(),
    result.signatureLevel(),
    result.signatureTimestamp()
);
pdfSignedEventPort.publishPdfSignedNotification(
    command.getSagaId(),
    command.getDocumentId(),
    command.getInvoiceNumber(),
    command.getDocumentType(),
    document.getId().toString(),
    result.signedPdfUrl(),
    result.signedPdfSize(),
    result.signatureLevel(),
    result.signatureTimestamp(),
    command.getCorrelationId()
);
```

For the **idempotent path** (`sendSuccessReply` private method), replace with:
```java
private void sendSuccessReply(ProcessPdfSigningCommand command, SignedPdfDocument document) {
    Instant timestamp = document.getSignatureTimestamp()
        .atZone(java.time.ZoneId.systemDefault()).toInstant();
    sagaReplyPort.publishSuccess(
        command.getSagaId(),
        command.getSagaStep(),
        command.getCorrelationId(),
        document.getId().toString(),
        document.getSignedPdfUrl(),
        document.getSignedPdfSize(),
        document.getTransactionId(),
        document.getCertificate(),
        document.getSignatureLevel(),
        timestamp
    );
    pdfSignedEventPort.publishPdfSignedNotification(
        command.getSagaId(),
        command.getDocumentId(),
        command.getInvoiceNumber(),
        command.getDocumentType(),
        document.getId().toString(),
        document.getSignedPdfUrl(),
        document.getSignedPdfSize(),
        document.getSignatureLevel(),
        timestamp,
        command.getCorrelationId()
    );
}
```

Replace `eventPublisher.publishFailure(...)` calls (two sites — max-retries path and catch block):
```java
// Before:
eventPublisher.publishFailure(
    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
    command.getDocumentId(), command.getInvoiceNumber(), command.getDocumentType(),
    "Maximum retry attempts exceeded for PDF signing"
);

// After:
sagaReplyPort.publishFailure(
    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
    "Maximum retry attempts exceeded for PDF signing"
);
pdfSignedEventPort.publishPdfSigningFailureNotification(
    command.getSagaId(), command.getDocumentId(), command.getInvoiceNumber(),
    command.getDocumentType(),
    "Maximum retry attempts exceeded for PDF signing",
    command.getCorrelationId()
);
```

Replace `eventPublisher.publishCompensated(...)` (compensation path — saga reply only, no notification):
```java
// Before:
eventPublisher.publishCompensated(
    command.getSagaId(), command.getSagaStep(), command.getCorrelationId()
);

// After:
sagaReplyPort.publishCompensated(
    command.getSagaId(), command.getSagaStep(), command.getCorrelationId()
);
```

Also replace compensation failure path:
```java
// Before:
eventPublisher.publishFailure(
    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
    command.getDocumentId(), "", command.getDocumentType(),
    "Compensation failed: " + e.getMessage()
);

// After:
sagaReplyPort.publishFailure(
    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
    "Compensation failed: " + e.getMessage()
);
```

**Step 2: Update `SagaCommandHandlerTest.java`**

Replace mock and import for `PdfSigningEventPublisher` with two port mocks:

```java
// Remove import:
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;

// Add imports:
import com.wpanther.pdfsigning.domain.port.out.PdfSignedEventPort;
import com.wpanther.pdfsigning.domain.port.out.PdfSagaReplyPort;
```

Replace mock field:
```java
// Before:
@Mock
private PdfSigningEventPublisher eventPublisher;

// After:
@Mock
private PdfSagaReplyPort sagaReplyPort;

@Mock
private PdfSignedEventPort pdfSignedEventPort;
```

Update all `verify(eventPublisher).publishSuccess(...)` assertions to verify both ports
separately. For example:

```java
// Before:
verify(eventPublisher).publishSuccess(
    eq("saga-123"), eq(SagaStep.SIGN_PDF), eq("corr-456"),
    eq("doc-789"), eq("INV-2024-001"), eq("INVOICE"),
    anyString(), eq("http://example.com/signed.pdf"),
    eq(54321L), eq("txn-abc"), eq("PEM-CERT"), eq("PAdES-BASELINE-B"), any()
);

// After:
verify(sagaReplyPort).publishSuccess(
    eq("saga-123"), eq(SagaStep.SIGN_PDF), eq("corr-456"),
    anyString(),                          // signedDocumentId
    eq("http://example.com/signed.pdf"),
    eq(54321L), eq("txn-abc"), eq("PEM-CERT"), eq("PAdES-BASELINE-B"), any()
);
verify(pdfSignedEventPort).publishPdfSignedNotification(
    eq("saga-123"), eq("doc-789"), eq("INV-2024-001"), eq("INVOICE"),
    anyString(),                          // signedDocumentId
    eq("http://example.com/signed.pdf"),
    eq(54321L), eq("PAdES-BASELINE-B"), any(), eq("corr-456")
);
```

Apply the same pattern for all `publishFailure` and `publishCompensated` verify calls.

**Step 3: Run `SagaCommandHandlerTest` to confirm it passes**
```bash
cd services/pdf-signing-service && mvn test -Dtest=SagaCommandHandlerTest
```
Expected: PASS

**Step 4: Delete the three publisher classes and their tests**
```bash
cd services/pdf-signing-service
rm src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/PdfSigningEventPublisher.java
rm src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/SagaReplyPublisher.java
rm src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/NotificationEventPublisher.java
rm src/test/java/com/wpanther/pdfsigning/infrastructure/messaging/PdfSigningEventPublisherTest.java
rm src/test/java/com/wpanther/pdfsigning/infrastructure/messaging/SagaReplyPublisherTest.java
rm src/test/java/com/wpanther/pdfsigning/infrastructure/messaging/NotificationEventPublisherTest.java
rmdir src/main/java/com/wpanther/pdfsigning/infrastructure/messaging
rmdir src/test/java/com/wpanther/pdfsigning/infrastructure/messaging
```

**Step 5: Run all tests**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS. If there are compile errors about missing `messaging.*` imports
elsewhere, fix them now.

**Step 6: Commit**
```bash
cd services/pdf-signing-service
git add -A
git commit -m "refactor: replace PdfSigningEventPublisher with PdfSignedEventPort + PdfSagaReplyPort"
```

---

# Task 6: Rename `adapter/secondary/` → `adapter/out/`

**Why:** Convention alignment — xml-signing-service uses `adapter/out/` for all outbound
adapters. This is a package rename only; no logic changes in any adapter file.

**Files (5 adapters + 5 test files):**
- Move: `adapter/secondary/csc/CscSigningAdapter.java` → `adapter/out/csc/`
- Move: `adapter/secondary/download/HttpDocumentDownloadAdapter.java` → `adapter/out/download/`
- Move: `adapter/secondary/pdf/PadesSignatureAdapter.java` → `adapter/out/pdf/`
- Move: `adapter/secondary/storage/LocalStorageAdapter.java` → `adapter/out/storage/`
- Move: `adapter/secondary/storage/S3StorageAdapter.java` → `adapter/out/storage/`
- Move corresponding test files from `adapter/secondary/` → `adapter/out/`

**Step 1: Move all adapter source files**
```bash
BASE=services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/infrastructure/adapter

mkdir -p $BASE/out/csc $BASE/out/download $BASE/out/pdf $BASE/out/storage

mv $BASE/secondary/csc/CscSigningAdapter.java         $BASE/out/csc/
mv $BASE/secondary/download/HttpDocumentDownloadAdapter.java $BASE/out/download/
mv $BASE/secondary/pdf/PadesSignatureAdapter.java      $BASE/out/pdf/
mv $BASE/secondary/storage/LocalStorageAdapter.java    $BASE/out/storage/
mv $BASE/secondary/storage/S3StorageAdapter.java       $BASE/out/storage/

rmdir $BASE/secondary/csc $BASE/secondary/download $BASE/secondary/pdf \
      $BASE/secondary/storage $BASE/secondary
```

**Step 2: Update package declaration in each moved file**

In each of the 5 files, change:
```java
// Before:
package com.wpanther.pdfsigning.infrastructure.adapter.secondary.csc;
// (or .download, .pdf, .storage)

// After:
package com.wpanther.pdfsigning.infrastructure.adapter.out.csc;
// (or .out.download, .out.pdf, .out.storage)
```

**Step 3: Move all adapter test files**
```bash
BASE_TEST=services/pdf-signing-service/src/test/java/com/wpanther/pdfsigning/infrastructure/adapter

mkdir -p $BASE_TEST/out/csc $BASE_TEST/out/download $BASE_TEST/out/pdf $BASE_TEST/out/storage

mv $BASE_TEST/secondary/csc/CscSigningAdapterTest.java         $BASE_TEST/out/csc/
mv $BASE_TEST/secondary/download/HttpDocumentDownloadAdapterTest.java $BASE_TEST/out/download/
mv $BASE_TEST/secondary/pdf/PadesSignatureAdapterTest.java      $BASE_TEST/out/pdf/
mv $BASE_TEST/secondary/storage/LocalStorageAdapterTest.java    $BASE_TEST/out/storage/
mv $BASE_TEST/secondary/storage/S3StorageAdapterTest.java       $BASE_TEST/out/storage/

rmdir $BASE_TEST/secondary/csc $BASE_TEST/secondary/download $BASE_TEST/secondary/pdf \
      $BASE_TEST/secondary/storage $BASE_TEST/secondary
```

**Step 4: Update package declaration in each moved test file**

In each of the 5 test files, change:
```java
// Before:
package com.wpanther.pdfsigning.infrastructure.adapter.secondary.csc;
// After:
package com.wpanther.pdfsigning.infrastructure.adapter.out.csc;
```
(and likewise for `.download`, `.pdf`, `.storage`)

**Step 5: Verify compilation**
```bash
cd services/pdf-signing-service && mvn compile
```
Expected: BUILD SUCCESS.

**Step 6: Run all tests**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

**Step 7: Commit**
```bash
cd services/pdf-signing-service
git add -A
git commit -m "refactor: rename adapter/secondary/ to adapter/out/ for convention alignment"
```

---

# Task 7: Move `SagaRouteConfig` → `adapter/in/camel/`; inject `SagaCommandPort`; delete `SagaCommandKafkaAdapter`

**Why:** The inbound Camel adapter must live in `adapter/in/camel/` (matching xml-signing-service)
and must inject `SagaCommandPort` (the inbound port interface) instead of the concrete
`SagaCommandHandler`. `SagaCommandKafkaAdapter` is deleted because its manual JSON-parse-then-
delegate pattern is superseded by Camel's `.unmarshal().json()` + direct port call.

**Files:**
- Move: `src/main/java/com/wpanther/pdfsigning/infrastructure/config/SagaRouteConfig.java`
      → `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`
- Delete: `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/primary/kafka/SagaCommandKafkaAdapter.java`
- Delete: `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/primary/kafka/SagaCommandKafkaAdapterTest.java`

**Step 1: Move `SagaRouteConfig.java` to new location**
```bash
mkdir -p services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel
mv services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/infrastructure/config/SagaRouteConfig.java \
   services/pdf-signing-service/src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/
```

**Step 2: Update package declaration and import in `SagaRouteConfig.java`**

```java
// Before:
package com.wpanther.pdfsigning.infrastructure.config;

import com.wpanther.pdfsigning.application.service.SagaCommandHandler;

// After:
package com.wpanther.pdfsigning.infrastructure.adapter.in.camel;

import com.wpanther.pdfsigning.domain.port.in.SagaCommandPort;
```

Change the injected field:
```java
// Before:
private final SagaCommandHandler sagaCommandHandler;

// After:
private final SagaCommandPort sagaCommandPort;
```

Update the two route `.process(...)` lambdas to call the port instead of the handler:

```java
// Command route — Before:
sagaCommandHandler.handleProcessCommand(cmd);

// After:
sagaCommandPort.handleProcessPdfSigning(cmd);
```

```java
// Compensation route — Before:
sagaCommandHandler.handleCompensation(cmd);

// After:
sagaCommandPort.handleCompensatePdfSigning(cmd);
```

No other changes to `SagaRouteConfig` — DLQ, retries, topic names, groupId, consumer settings
all remain identical.

**Step 3: Delete `SagaCommandKafkaAdapter` and its test**
```bash
cd services/pdf-signing-service
rm src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/primary/kafka/SagaCommandKafkaAdapter.java
rm src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/primary/kafka/SagaCommandKafkaAdapterTest.java
rmdir src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/primary/kafka
rmdir src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/primary
rmdir src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/primary/kafka
rmdir src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/primary
```

**Step 4: Verify no remaining references to old packages**
```bash
grep -r "infrastructure\.config\.SagaRouteConfig\|adapter\.primary\|SagaCommandHandler" \
  services/pdf-signing-service/src/main/java --include="*.java"
```
Expected: no output (only `SagaCommandHandler.java` itself is allowed to reference its own class).

**Step 5: Run all tests**
```bash
cd services/pdf-signing-service && mvn test
```
Expected: BUILD SUCCESS

**Step 6: Commit**
```bash
cd services/pdf-signing-service
git add -A
git commit -m "refactor: move SagaRouteConfig to adapter/in/camel/; inject SagaCommandPort; delete SagaCommandKafkaAdapter"
```

---

# Task 8: Final Verification

**Why:** Confirm the complete migration is clean — all tests pass, coverage threshold met,
no stale references to old packages remain.

**Step 1: Verify no stale package references**
```bash
grep -r "domain\.repository\|domain\.port\b[^.]" \
  services/pdf-signing-service/src --include="*.java"
```
Expected: no output (the only `domain.port` references should be `domain.port.in` or `domain.port.out`).

```bash
grep -r "infrastructure\.messaging\|adapter\.secondary\|adapter\.primary" \
  services/pdf-signing-service/src --include="*.java"
```
Expected: no output.

```bash
grep -r "PdfSigningEventPublisher\|SagaReplyPublisher\|NotificationEventPublisher\|SagaCommandKafkaAdapter" \
  services/pdf-signing-service/src --include="*.java"
```
Expected: no output.

**Step 2: Run full test suite with coverage**
```bash
cd services/pdf-signing-service && mvn verify
```
Expected: BUILD SUCCESS with JaCoCo coverage ≥ 80% per package.

**Step 3: Verify final package structure matches design**
```bash
find services/pdf-signing-service/src/main/java -name "*.java" | sort
```

Confirm the following directories exist and no others under `adapter/`:
- `adapter/in/camel/SagaRouteConfig.java`
- `adapter/out/csc/CscSigningAdapter.java`
- `adapter/out/download/HttpDocumentDownloadAdapter.java`
- `adapter/out/messaging/OutboxPdfSignedEventAdapter.java`
- `adapter/out/messaging/OutboxSagaReplyAdapter.java`
- `adapter/out/pdf/PadesSignatureAdapter.java`
- `adapter/out/storage/LocalStorageAdapter.java`
- `adapter/out/storage/S3StorageAdapter.java`

Confirm `domain/port/` contains only:
- `in/SagaCommandPort.java`
- `out/DocumentDownloadPort.java`
- `out/DocumentStoragePort.java`
- `out/PdfGenerationPort.java`
- `out/PdfSagaReplyPort.java`
- `out/PdfSignedEventPort.java`
- `out/SignedPdfDocumentRepository.java`
- `out/SigningPort.java`

Confirm `domain/repository/` does NOT exist.
Confirm `infrastructure/messaging/` does NOT exist.

**Step 4: Commit final verification**
```bash
cd services/pdf-signing-service
git commit --allow-empty -m "refactor: pdf-signing hexagonal migration complete (Approach B)"
```

---

## Summary

| Task | Description | Files Changed |
|------|-------------|---------------|
| 1 | Move flat `domain/port/` → `domain/port/out/` | 4 ports + 6 consumers (import only) |
| 2 | Move `SignedPdfDocumentRepository` → `domain/port/out/` | 1 file + 4 consumers |
| 3 | Create `PdfSignedEventPort` + `PdfSagaReplyPort` | 2 new |
| 4 | Create `OutboxPdfSignedEventAdapter` + `OutboxSagaReplyAdapter` | 2 new + 2 new tests |
| 5 | Update `SagaCommandHandler`; delete 3 publisher classes | 1 modified + 3 deleted + 3 test deleted |
| 6 | Rename `adapter/secondary/` → `adapter/out/` | 5 moved + 5 tests moved |
| 7 | Move `SagaRouteConfig`; inject port; delete `SagaCommandKafkaAdapter` | 1 moved + 2 deleted |
| 8 | Final verification | grep + mvn verify |

**Zero database schema changes. Zero Kafka topic changes. Zero Kafka consumer group changes.**
