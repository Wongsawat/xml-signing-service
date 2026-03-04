# Hexagonal Architecture Migration Design
**Date:** 2026-03-04
**Scope:** `xml-signing-service` + `pdf-signing-service`
**Approach:** Incremental-by-port (Approach A) — tests stay green after each step

---

## Problem Statement

Both signing services are *partially* hexagonal but have structural violations:

1. **Ports import infrastructure DTOs** — `CscAuthorizationPort` and `CscSignaturePort` use `CSCAuthorizeRequest/Response` and `CSCSignatureRequest/Response` from `infrastructure.client.csc.dto`, forcing the domain to know about HTTP serialization details.
2. **Feign clients directly implement domain ports** — `CSCAuthClient extends CscAuthorizationPort` and `CSCSignatureClient extends CscSignaturePort`. This is why the port signatures are polluted with infra types.
3. **`XmlSigningServiceImpl` is misplaced** — it implements the `XmlSigningService` domain interface but lives in `infrastructure.client`.
4. **Missing outbound ports** — `SagaCommandHandler` (application layer) directly depends on three concrete infrastructure classes: `MinioStorageService`, `EventPublisher`, and `SagaReplyPublisher`.
5. **No inbound port** — the Camel route calls `SagaCommandHandler` directly instead of through a defined port interface.

---

## Target Architecture

### Port Types (Hexagonal)

```
   External World (Kafka/Camel)
          │
    [SagaCommandPort]         ← PRIMARY / INBOUND port
          │
   SagaCommandHandler         ← APPLICATION layer (depends only on ports)
          │
   ┌──────┴──────────────────────────────┐
   │  Out-ports (SECONDARY / OUTBOUND)   │
   ├─────────────────────────────────────┤
   │ CscAuthorizationPort                │
   │ CscSignaturePort                    │
   │ XmlStoragePort                      │
   │ XmlSignedEventPort                  │
   │ SagaReplyPort                       │
   │ SignedXmlDocumentRepository         │
   └─────────────────────────────────────┘
          │
   Infrastructure Adapters (CscAuthorizationAdapter, MinioXmlStorageAdapter, etc.)
          │
   External Systems (CSC API, MinIO, Outbox/Kafka, PostgreSQL)
```

### Target Package Structure

```
com.wpanther.xmlsigning
├── domain/
│   ├── model/
│   │   ├── SignedXmlDocument.java         (MODIFIED: uses XmlStorageKey)
│   │   ├── SignedXmlDocumentId.java       (unchanged)
│   │   ├── SigningStatus.java             (unchanged)
│   │   ├── DocumentType.java              (unchanged)
│   │   ├── XmlStorageKey.java             (NEW: value object for storage keys)
│   │   └── csc/
│   │       ├── CscAuthorizeCommand.java   (NEW: replaces CSCAuthorizeRequest at port)
│   │       ├── CscAuthorizeResult.java    (NEW: replaces CSCAuthorizeResponse at port)
│   │       ├── CscSignHashCommand.java    (NEW: replaces CSCSignatureRequest at port)
│   │       └── CscSignHashResult.java     (NEW: replaces CSCSignatureResponse at port)
│   ├── exception/                         (unchanged)
│   ├── service/                           (unchanged)
│   │   ├── XmlSigningService.java
│   │   ├── SigningResult.java
│   │   └── DocumentTypeDetectionService.java
│   └── port/
│       ├── in/                            (NEW)
│       │   └── SagaCommandPort.java
│       └── out/                           (existing ports + new + moved)
│           ├── CscAuthorizationPort.java  (MODIFIED: uses CscAuthorize* types)
│           ├── CscSignaturePort.java      (MODIFIED: uses CscSignHash* types)
│           ├── SignedXmlDocumentRepository.java  (MOVED from domain/repository/)
│           ├── XmlStoragePort.java        (NEW)
│           ├── XmlSignedEventPort.java    (NEW)
│           └── SagaReplyPort.java         (NEW)
├── application/
│   └── service/
│       ├── SagaCommandHandler.java        (MODIFIED: implements SagaCommandPort, no infra deps)
│       └── XmlSigningServiceImpl.java     (MOVED from infrastructure/client/)
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   └── camel/
    │   │       └── SagaRouteConfig.java   (MOVED from infrastructure/config/)
    │   └── out/
    │       ├── csc/
    │       │   ├── CscAuthorizationAdapter.java   (NEW)
    │       │   └── CscSignatureAdapter.java       (NEW)
    │       ├── storage/
    │       │   └── MinioXmlStorageAdapter.java    (NEW)
    │       └── messaging/
    │           ├── OutboxXmlSignedEventAdapter.java  (NEW, replaces EventPublisher)
    │           └── OutboxSagaReplyAdapter.java       (NEW, replaces SagaReplyPublisher)
    ├── client/csc/                        (MODIFIED: Feign clients no longer extend domain ports)
    ├── config/                            (unchanged: FeignConfig, MinioConfig, OutboxConfig)
    ├── embedder/                          (unchanged)
    ├── messaging/                         (DELETED: EventPublisher, SagaReplyPublisher)
    ├── persistence/                       (unchanged)
    ├── storage/                           (unchanged: MinioStorageService)
    └── util/                             (unchanged)
```

---

## Port Contracts

### Inbound Port

```java
// domain/port/in/SagaCommandPort.java
public interface SagaCommandPort {
    void handleProcessCommand(ProcessXmlSigningCommand command);
    void handleCompensation(CompensateXmlSigningCommand command);
}
```

`SagaCommandHandler` implements this. The Camel route injects `SagaCommandPort`.

### Cleaned CSC Ports (domain types only)

```java
// domain/model/csc/CscAuthorizeCommand.java
public record CscAuthorizeCommand(
    String clientId, String credentialId, String numSignatures,
    String hashAlgorithm, String[] documentDigests, String description
) {}

// domain/model/csc/CscAuthorizeResult.java
public record CscAuthorizeResult(String sadToken, String transactionId) {}

// domain/port/out/CscAuthorizationPort.java  — NO infrastructure imports
public interface CscAuthorizationPort {
    CscAuthorizeResult authorize(CscAuthorizeCommand command) throws CscAuthorizationException;
}

// domain/model/csc/CscSignHashCommand.java
public record CscSignHashCommand(
    String clientId, String credentialId, String sadToken,
    String hashAlgorithm, String[] documentDigests,
    String signatureType, String signatureLevel,
    String signatureForm, String digestAlgorithm, long signDate
) {}

// domain/model/csc/CscSignHashResult.java
public record CscSignHashResult(String[] signatures, String certificate) {}

// domain/port/out/CscSignaturePort.java  — NO infrastructure imports
public interface CscSignaturePort {
    CscSignHashResult signHash(CscSignHashCommand command) throws CscSignatureException;
}
```

### New Outbound Ports

```java
// domain/port/out/XmlStoragePort.java
public interface XmlStoragePort {
    XmlStorageKey uploadOriginalXml(String invoiceId, DocumentType type, String xmlContent);
    XmlStorageKey uploadSignedXml(String invoiceId, DocumentType type, String xmlContent);
    String buildUrl(XmlStorageKey key);
    void delete(XmlStorageKey key);
}

// domain/port/out/XmlSignedEventPort.java
public interface XmlSignedEventPort {
    void publishXmlSigned(String invoiceId, String invoiceNumber,
                          DocumentType documentType, String correlationId);
}

// domain/port/out/SagaReplyPort.java
public interface SagaReplyPort {
    void publishSuccess(String sagaId, SagaStep step, String correlationId,
                        String signedXmlUrl, Long signedXmlSize);
    void publishFailure(String sagaId, SagaStep step, String correlationId, String errorMessage);
    void publishCompensated(String sagaId, SagaStep step, String correlationId);
}
```

---

## Adapter Design

### CSC Adapters (introduce mapping layer between domain and Feign)

```java
// infrastructure/adapter/out/csc/CscAuthorizationAdapter.java
@Component @RequiredArgsConstructor
public class CscAuthorizationAdapter implements CscAuthorizationPort {
    private final CSCAuthClient feignClient;

    @Override
    public CscAuthorizeResult authorize(CscAuthorizeCommand cmd) throws CscAuthorizationException {
        CSCAuthorizeRequest request = toFeignRequest(cmd);
        CSCAuthorizeResponse response = feignClient.authorize(request);
        return new CscAuthorizeResult(response.getSAD(), response.getTransactionID());
    }
    // private mapping methods
}

// CSCAuthClient becomes a plain @FeignClient — no longer extends CscAuthorizationPort
```

### Messaging Adapters (talk to OutboxService directly; replace EventPublisher + SagaReplyPublisher)

```java
// infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java
@Component @RequiredArgsConstructor @Slf4j
public class OutboxSagaReplyAdapter implements SagaReplyPort {
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Override @Transactional(Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep step, String correlationId,
                               String signedXmlUrl, Long signedXmlSize) {
        XmlSigningReplyEvent reply = XmlSigningReplyEvent.success(sagaId, step, correlationId,
                signedXmlUrl, signedXmlSize);
        outboxService.saveWithRouting(reply, "SignedXmlDocument", sagaId,
                "saga.reply.xml-signing", sagaId, toJson(Map.of(
                    "sagaId", sagaId, "correlationId", correlationId, "status", "SUCCESS")));
    }
    // ... failure, compensated
}
```

`EventPublisher` and `SagaReplyPublisher` are **deleted** — their logic moves into these adapters.

### Storage Adapter

```java
// infrastructure/adapter/out/storage/MinioXmlStorageAdapter.java
@Component @RequiredArgsConstructor
public class MinioXmlStorageAdapter implements XmlStoragePort {
    private final MinioStorageService minioService;  // unchanged infra service

    @Override
    public XmlStorageKey uploadOriginalXml(String invoiceId, DocumentType type, String xml) {
        return new XmlStorageKey(minioService.uploadOriginalXml(invoiceId, type.name(), xml));
    }
    // ... uploadSignedXml, buildUrl, delete
}
```

### Inbound Adapter (Camel)

`SagaRouteConfig` is moved to `infrastructure/adapter/in/camel/` and updated to inject `SagaCommandPort`:

```java
private final SagaCommandPort sagaCommandPort;   // was: SagaCommandHandler
// route: .bean(sagaCommandPort, "handleProcessCommand")
```

---

## Updated SagaCommandHandler Dependencies

After migration, `SagaCommandHandler implements SagaCommandPort` with these dependencies — all ports, zero infrastructure classes:

```java
private final SignedXmlDocumentRepository documentRepository;  // port
private final XmlSigningService signingService;                // domain service
private final DocumentTypeDetectionService typeDetectionService; // domain service
private final XmlStoragePort xmlStoragePort;                   // NEW port
private final XmlSignedEventPort xmlSignedEventPort;           // NEW port
private final SagaReplyPort sagaReplyPort;                     // NEW port
private final TransactionTemplate transactionTemplate;         // Spring utility (acceptable)
```

---

## Migration Steps (xml-signing-service)

Each step = one commit. Run `mvn test` after every step.

| Step | What changes | Files added | Files modified | Files deleted |
|------|---|---|---|---|
| 1 | Create CSC domain value objects | `CscAuthorizeCommand`, `CscAuthorizeResult`, `CscSignHashCommand`, `CscSignHashResult` (all in `domain/model/csc/`) | — | — |
| 2 | Clean up CSC ports + introduce adapters | `CscAuthorizationAdapter`, `CscSignatureAdapter` (in `infrastructure/adapter/out/csc/`) | `CscAuthorizationPort`, `CscSignaturePort`, `CSCAuthClient`, `CSCSignatureClient`, `XmlSigningServiceImpl` | — |
| 3 | Move `XmlSigningServiceImpl` to `application/service/` | — | `XmlSigningServiceImpl` (package change) | Old copy |
| 4 | Add `XmlStorageKey` value object; update `SignedXmlDocument` + JPA mapper | `XmlStorageKey` | `SignedXmlDocument`, `SignedXmlDocumentMapper`, `SignedXmlDocumentEntity` | — |
| 5 | Add `XmlStoragePort` + create `MinioXmlStorageAdapter` | `XmlStoragePort`, `MinioXmlStorageAdapter` | — | — |
| 6 | Add `XmlSignedEventPort` and `SagaReplyPort` interfaces | `XmlSignedEventPort`, `SagaReplyPort` | — | — |
| 7 | Create messaging adapters; delete old publisher classes | `OutboxXmlSignedEventAdapter`, `OutboxSagaReplyAdapter` | — | `EventPublisher`, `SagaReplyPublisher` |
| 8 | Add `SagaCommandPort` inbound interface | `SagaCommandPort` | — | — |
| 9 | `SagaCommandHandler` implements `SagaCommandPort`; swap 3 concrete infra deps to ports | — | `SagaCommandHandler` | — |
| 10 | Move `SagaRouteConfig` → `infrastructure/adapter/in/camel/`; inject `SagaCommandPort` | New location | `SagaRouteConfig` | Old copy |
| 11 | Move `SignedXmlDocumentRepository` → `domain/port/out/`; delete `domain/repository/` | New location | Import references | Old copy |

---

## Migration Steps (pdf-signing-service)

Same 11-step pattern. Key differences:

- Domain types are `SignedPdfDocument`, `PdfStorageKey`
- Ports: `PdfStoragePort`, `PdfSignedEventPort`, `SagaReplyPort` (same pattern, different topic `saga.reply.pdf-signing`)
- Inbound port: `SagaCommandPort` (pdf variant)
- CSC value objects can be the same `domain.model.csc.*` records (copy or extract to shared library)

---

## Testing Strategy

- **After each step:** `mvn test` (all unit tests must pass)
- **After step 5:** Run `KafkaConsumerIntegrationTest` (exercises storage path)
- **After step 7:** Run full integration suite (messaging adapters now active)
- **After step 9:** Run `SagaCommandHandlerTest` (all port dependencies verifiable via mocks)
- **After step 11 (xml-signing complete):** Run CDC integration tests
- **After pdf-signing step 11:** Full integration suite on pdf-signing

New unit tests to add for each new adapter class (mock underlying Feign/MinIO/Outbox dependencies).

---

## What Changes Per Service Summary

| Component | xml-signing | pdf-signing |
|---|---|---|
| New domain value objects | 5 (`CscAuthorize*`, `CscSignHash*`, `XmlStorageKey`) | 5 (same CSC + `PdfStorageKey`) |
| New port interfaces | 5 (`SagaCommandPort` + 4 outbound) | 5 |
| New adapters | 5 (`CscAuth`, `CscSig`, `Minio`, `Event`, `Reply`) | 5 |
| Files deleted | 2 (`EventPublisher`, `SagaReplyPublisher`) | 2 |
| Files moved | 3 (`XmlSigningServiceImpl`, `SagaRouteConfig`, `Repository`) | 3 |
| Files modified | ~8 (domain model, existing ports, Feign clients, handler) | ~8 |

**Total net new files per service: ~10. Zero database changes. Zero Kafka topic changes.**
