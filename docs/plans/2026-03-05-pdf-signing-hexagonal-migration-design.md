# pdf-signing-service Hexagonal Architecture Migration Design

**Date:** 2026-03-05
**Scope:** `pdf-signing-service` — remaining violations after xml-signing-service migration complete
**Approach:** Approach B (Full Convention Alignment) — mirror xml-signing-service conventions exactly
**Predecessor:** `docs/plans/2026-03-04-hexagonal-migration-design.md` (xml-signing-service, Tasks 1–10 complete)

---

## Context

xml-signing-service Tasks 1–10 are complete on branch `feat/hexagonal-migration`. The
pdf-signing-service was partially hexagonal from the start but has structural violations that
prevent full alignment with the xml-signing-service conventions established in that migration.

---

## Violations to Fix

### Architectural (break hexagonal purity)

1. **`SagaCommandHandler` depends on `PdfSigningEventPublisher`** (concrete infra class in
   `infrastructure.messaging`) — application layer must only depend on domain port interfaces.

2. **`SagaRouteConfig` calls `sagaCommandHandler.handleProcessCommand()` directly** — the inbound
   Camel adapter bypasses the inbound port interface `SagaCommandPort`. It should inject
   `SagaCommandPort`, not the concrete `SagaCommandHandler`.

3. **`SignedPdfDocumentRepository` lives in `domain.repository`** — outbound port interfaces must
   live in `domain.port.out`.

### Convention (naming misalignment with xml-signing-service)

4. **Flat `domain/port/` for outbound ports** — `DocumentDownloadPort`, `DocumentStoragePort`,
   `PdfGenerationPort`, `SigningPort` are in `domain.port` instead of `domain.port.out`.

5. **`adapter/primary/kafka/` and `adapter/secondary/`** — must become `adapter/in/camel/` and
   `adapter/out/` to match xml-signing-service conventions.

6. **`infrastructure/messaging/`** — three concrete publisher classes that should either be ports
   or adapter implementations, not free-standing infrastructure classes.

7. **`SagaRouteConfig` in `infrastructure/config/`** — inbound adapter must live in
   `adapter/in/camel/`, not in the config package.

---

## Target Package Structure

```
com.wpanther.pdfsigning
├── domain/
│   ├── event/                              (unchanged)
│   ├── model/                              (unchanged)
│   ├── port/
│   │   ├── in/
│   │   │   └── SagaCommandPort.java        (unchanged — already here)
│   │   └── out/
│   │       ├── DocumentDownloadPort.java   (moved from domain/port/)
│   │       ├── DocumentStoragePort.java    (moved from domain/port/)
│   │       ├── PdfGenerationPort.java      (moved from domain/port/)
│   │       ├── SigningPort.java            (moved from domain/port/)
│   │       ├── SignedPdfDocumentRepository.java  (moved from domain/repository/)
│   │       ├── PdfSignedEventPort.java     (NEW)
│   │       └── PdfSagaReplyPort.java       (NEW)
│   ├── repository/                         (DELETED after move)
│   └── service/                            (unchanged)
│       └── DomainPdfSigningService.java    (import update only)
├── application/
│   └── service/
│       └── SagaCommandHandler.java         (MODIFIED: PdfSigningEventPublisher → two ports)
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   └── camel/
    │   │       └── SagaRouteConfig.java    (MOVED from infrastructure/config/;
    │   │                                    injects SagaCommandPort, not SagaCommandHandler)
    │   └── out/
    │       ├── csc/
    │       │   └── CscSigningAdapter.java          (MOVED from adapter/secondary/csc/)
    │       ├── download/
    │       │   └── HttpDocumentDownloadAdapter.java (MOVED from adapter/secondary/download/)
    │       ├── messaging/
    │       │   ├── OutboxPdfSignedEventAdapter.java (NEW — absorbs NotificationEventPublisher)
    │       │   └── OutboxSagaReplyAdapter.java      (NEW — absorbs SagaReplyPublisher)
    │       ├── pdf/
    │       │   └── PadesSignatureAdapter.java       (MOVED from adapter/secondary/pdf/)
    │       └── storage/
    │           ├── LocalStorageAdapter.java         (MOVED from adapter/secondary/storage/)
    │           └── S3StorageAdapter.java            (MOVED from adapter/secondary/storage/)
    ├── adapter/primary/                    (DELETED — SagaCommandKafkaAdapter deleted)
    ├── client/                             (unchanged)
    ├── config/
    │   ├── CSCErrorDecoder.java            (unchanged)
    │   ├── FeignConfig.java                (unchanged)
    │   └── properties/                     (unchanged)
    ├── messaging/                          (DELETED — all 3 publishers absorbed into adapters)
    ├── pdf/                                (unchanged)
    └── persistence/                        (import update only)
        └── SignedPdfDocumentRepositoryAdapter.java
```

**Summary: 22 files modified/moved, 3 new files, 3 files deleted.**
Zero database schema changes. Zero Kafka topic changes.

---

## New Port Contracts

### `domain/port/out/PdfSignedEventPort.java`

Replaces `NotificationEventPublisher` as the domain abstraction for notification events.

```java
public interface PdfSignedEventPort {
    void publishPdfSignedNotification(
        String sagaId, String invoiceId, String invoiceNumber, String documentType,
        String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
        String signatureLevel, Instant signatureTimestamp, String correlationId);

    void publishPdfSigningFailureNotification(
        String sagaId, String invoiceId, String invoiceNumber, String documentType,
        String errorMessage, String correlationId);
}
```

### `domain/port/out/PdfSagaReplyPort.java`

Replaces `SagaReplyPublisher` as the domain abstraction for orchestrator replies.

```java
public interface PdfSagaReplyPort {
    void publishSuccess(
        String sagaId, SagaStep sagaStep, String correlationId,
        String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
        String transactionId, String certificate, String signatureLevel,
        Instant signatureTimestamp);

    void publishFailure(String sagaId, SagaStep sagaStep, String correlationId,
                        String errorMessage);

    void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId);
}
```

---

## Adapter Implementations

### `OutboxPdfSignedEventAdapter` implements `PdfSignedEventPort`

Absorbs `NotificationEventPublisher` body verbatim. Same `OutboxService.saveWithRouting()`
calls, same topic routing via `KafkaProperties`. Requires `@Transactional(propagation = MANDATORY)`.

### `OutboxSagaReplyAdapter` implements `PdfSagaReplyPort`

Absorbs `SagaReplyPublisher` body verbatim. Same `OutboxService.saveWithRouting()` calls.
Requires `@Transactional(propagation = MANDATORY)`.

### Updated `SagaCommandHandler`

Replaces single `PdfSigningEventPublisher` injection with two port injections:

```java
// Before
private final PdfSigningEventPublisher eventPublisher;

// After
private final PdfSignedEventPort pdfSignedEventPort;
private final PdfSagaReplyPort sagaReplyPort;
```

Each `eventPublisher.publishSuccess(...)` call becomes two sequential calls:
`sagaReplyPort.publishSuccess(...)` then `pdfSignedEventPort.publishPdfSignedNotification(...)`.

Each `eventPublisher.publishFailure(...)` call becomes two sequential calls:
`sagaReplyPort.publishFailure(...)` then `pdfSignedEventPort.publishPdfSigningFailureNotification(...)`.

`eventPublisher.publishCompensated(...)` becomes `sagaReplyPort.publishCompensated(...)` only
(no notification event for compensation — same as current behaviour).

### `SagaRouteConfig` (inbound adapter)

Moved to `adapter/in/camel/`. Injects `SagaCommandPort` instead of `SagaCommandHandler`.
Camel routes call `sagaCommandPort.handleProcessPdfSigning(cmd)` and
`sagaCommandPort.handleCompensatePdfSigning(cmd)`. DLQ/retry errorHandler unchanged.

`SagaCommandKafkaAdapter` is deleted because its JSON-parse-then-delegate pattern is
superseded by Camel's `.unmarshal().json(...)` + direct `SagaCommandPort` call.

---

## Migration Steps

Execute in order — each step leaves `mvn test` green.

| Step | Action | Files |
|------|--------|-------|
| 1 | Move `domain/port/*.java` → `domain/port/out/`; update all imports | 4 port files + 5 adapter imports + `DomainPdfSigningService` |
| 2 | Move `SignedPdfDocumentRepository` → `domain/port/out/`; delete `domain/repository/` | 1 port file + `SagaCommandHandler` + `DomainPdfSigningService` + `SignedPdfDocumentRepositoryAdapter` |
| 3 | Create `PdfSignedEventPort` + `PdfSagaReplyPort` in `domain/port/out/` | 2 new files |
| 4 | Create `OutboxPdfSignedEventAdapter` + `OutboxSagaReplyAdapter` in `adapter/out/messaging/` | 2 new files + 2 new test files |
| 5 | Update `SagaCommandHandler`: replace `PdfSigningEventPublisher` with two ports; delete 3 publisher classes | 1 modified + 3 deleted; update `SagaCommandHandlerTest` |
| 6 | Rename `adapter/secondary/` → `adapter/out/` (5 adapters + 5 test files) | Package declaration only |
| 7 | Move `SagaRouteConfig` → `adapter/in/camel/`; inject `SagaCommandPort`; delete `SagaCommandKafkaAdapter` | 1 moved + 1 deleted + 1 test deleted |
| 8 | Final verification: `mvn test` green, 80% coverage met | — |

---

## Testing Strategy

- **Steps 1–2:** `mvn compile` only — package renames, no logic changes, no new tests.
- **Step 3:** No tests — pure interfaces.
- **Step 4:** `OutboxPdfSignedEventAdapterTest` + `OutboxSagaReplyAdapterTest` — mock `OutboxService`,
  assert correct topic routing and event type. Mirror deleted `PdfSigningEventPublisherTest` /
  `SagaReplyPublisherTest` / `NotificationEventPublisherTest`.
- **Step 5:** Update `SagaCommandHandlerTest` — replace `@Mock PdfSigningEventPublisher` with
  `@Mock PdfSignedEventPort` + `@Mock PdfSagaReplyPort`.
- **Step 6:** Move test files from `adapter/secondary/**Test` → `adapter/out/**Test`. Package
  declaration change only — zero logic changes.
- **Step 7:** Delete `SagaCommandKafkaAdapterTest`. No replacement needed — route logic is
  unchanged; only the injected type changes from concrete class to port interface.

---

## What Does NOT Change

- All Kafka topic names (`saga.command.pdf-signing`, `saga.reply.pdf-signing`, etc.)
- All database schemas (Flyway migrations untouched)
- `DomainPdfSigningService` business logic
- CSC Feign clients (`CSCAuthClient`, `CSCApiClient`)
- Outbox event payloads (`PdfSigningReplyEvent`, `PdfSignedNotificationEvent`, etc.)
- `persistence/` package
- `pdf/` package (PAdES embedder, certificate utilities)
- `config/properties/` and `FeignConfig`, `CSCErrorDecoder`
