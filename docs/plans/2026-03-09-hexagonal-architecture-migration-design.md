# Hexagonal Architecture Migration Design (Canonical Alignment)

**Date:** 2026-03-09
**Service:** xml-signing-service (port 8086)
**Type:** Refactor — package rename + relocation + one new port extraction, no business logic changes
**Strategy:** Phase-by-phase incremental (one commit per logical group, tests green after each)

---

## Context

The xml-signing-service is ~75% through a hexagonal migration (commits through `71ab94f`): adapter/in/out split, outbound port interfaces, outbox pattern, and Camel route injection are in place. This migration completes **canonical alignment** with the layout established across all other services (invoice-pdf, taxinvoice-pdf, ebms-sending, notification, orchestrator, document-intake, document-storage, pdf-signing):

- `domain/` ← `application/` ← `infrastructure/` (strict dependency rule)
- `application/usecase/` for inbound port interfaces and their implementations
- `domain/repository/` for domain-owned repository ports
- `application/port/out/` for non-domain outbound ports
- `application/dto/event/` for Kafka wire DTOs (not domain events)
- `application/dto/csc/` for CSC API command/result records (port parameter types)
- `infrastructure/adapter/out/<concern>/` owns its Feign clients, utilities, and DTOs
- `infrastructure/config/` with concern-based sub-packages

**Remaining gaps:**

| Current | Target | Change |
|---|---|---|
| `domain/port/in/SagaCommandPort` | `application/usecase/` | Move — inbound port belongs in application |
| `domain/port/out/SignedXmlDocumentRepository` | `domain/repository/` | Move — repository is domain-owned |
| `domain/port/out/{CscAuthorizationPort, CscSignaturePort, XmlStoragePort, XmlSignedEventPort, SagaReplyPort}` | `application/port/out/` | Move — application-layer contracts |
| `domain/event/*` (5 classes) | `application/dto/event/` | Move — Kafka wire DTOs, not domain events |
| `domain/model/csc/*` (4 records) | `application/dto/csc/` | Move — port parameter types, not domain model |
| `domain/service/XmlSigningService` + `SigningResult` | `application/usecase/` | Move — orchestrates via ports (application concern) |
| `application/service/SagaCommandHandler` + `XmlSigningServiceImpl` | `application/usecase/` | Merge — collapse application/service into usecase |
| `infrastructure/client/csc/` | `infrastructure/adapter/out/csc/client/` + `dto/` | Consolidate alongside CSC adapters |
| `infrastructure/embedder/XadesSignatureEmbedder` | `infrastructure/adapter/out/xml/` | Consolidate + implement new XadesEmbeddingPort |
| `infrastructure/util/SecureXmlParser` | `infrastructure/adapter/out/xml/` | Consolidate alongside XadesSignatureEmbedder |
| `infrastructure/messaging/CommandValidator` | `infrastructure/adapter/in/camel/` | Consolidate — Camel Processor belongs with Camel routes |
| `infrastructure/storage/MinioStorageService` | DELETE | Legacy, unused (replaced by MinioXmlStorageAdapter) |
| `infrastructure/config/` flat | `infrastructure/config/{feign,minio,outbox}/` | Concern sub-packages |
| *(new)* | `application/port/out/XadesEmbeddingPort` | Extract — decouples XmlSigningServiceImpl from infrastructure |

---

## Target Package Structure

```
com.wpanther.xmlsigning/
├── domain/
│   ├── model/                              # UNCHANGED
│   │   ├── SignedXmlDocument.java
│   │   ├── SignedXmlDocumentId.java
│   │   ├── SigningStatus.java
│   │   ├── DocumentType.java
│   │   └── XmlStorageKey.java
│   │   # domain/model/csc/ FULLY REMOVED
│   ├── exception/                          # UNCHANGED
│   │   ├── XmlSigningException.java
│   │   ├── CscAuthorizationException.java
│   │   ├── CscSignatureException.java
│   │   ├── DocumentStorageException.java
│   │   └── XmlValidationException.java
│   ├── service/                            # REDUCED — only pure domain logic remains
│   │   └── DocumentTypeDetectionService.java   # stays (depends only on domain/model/DocumentType)
│   │   # XmlSigningService + SigningResult → application/usecase/
│   └── repository/                         # NEW — moved from domain/port/out/
│       └── SignedXmlDocumentRepository.java
│   # domain/port/   FULLY REMOVED
│   # domain/event/  FULLY REMOVED
│
├── application/
│   ├── usecase/                            # MERGED: domain/port/in/ + application/service/ + partial domain/service/
│   │   ├── SagaCommandPort.java            # MOVED from domain/port/in/
│   │   ├── XmlSigningService.java          # MOVED from domain/service/
│   │   ├── SigningResult.java              # MOVED from domain/service/ (travels with its service)
│   │   ├── SagaCommandHandler.java         # MOVED from application/service/
│   │   └── XmlSigningServiceImpl.java      # MOVED from application/service/
│   ├── port/out/                           # MOVED from domain/port/out/ + NEW port
│   │   ├── CscAuthorizationPort.java
│   │   ├── CscSignaturePort.java
│   │   ├── XmlStoragePort.java
│   │   ├── XmlSignedEventPort.java
│   │   ├── SagaReplyPort.java
│   │   └── XadesEmbeddingPort.java         # NEW — extracted to decouple XmlSigningServiceImpl
│   └── dto/
│       ├── event/                          # MOVED from domain/event/
│       │   ├── ProcessXmlSigningCommand.java
│       │   ├── CompensateXmlSigningCommand.java
│       │   ├── XmlSigningReplyEvent.java
│       │   ├── XmlSignedEvent.java
│       │   └── XmlSigningRequestedEvent.java
│       └── csc/                            # MOVED from domain/model/csc/
│           ├── CscAuthorizeCommand.java
│           ├── CscAuthorizeResult.java
│           ├── CscSignHashCommand.java
│           └── CscSignHashResult.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   └── camel/                      # EXPANDED
    │   │       ├── SagaRouteConfig.java    # UNCHANGED
    │   │       └── CommandValidator.java   # MOVED from infrastructure/messaging/
    │   └── out/
    │       ├── csc/                        # EXPANDED — absorbs infrastructure/client/csc/
    │       │   ├── CscAuthorizationAdapter.java  # UNCHANGED
    │       │   ├── CscSignatureAdapter.java      # UNCHANGED
    │       │   ├── client/                 # MOVED from infrastructure/client/csc/
    │       │   │   ├── CSCAuthClient.java
    │       │   │   └── CSCSignatureClient.java
    │       │   └── dto/                    # MOVED from infrastructure/client/csc/dto/
    │       │       ├── CSCAuthorizeRequest.java
    │       │       ├── CSCAuthorizeResponse.java
    │       │       ├── CSCSignatureRequest.java
    │       │       ├── CSCSignatureResponse.java
    │       │       ├── SignatureAttributes.java
    │       │       └── SignatureData.java
    │       ├── storage/                    # UNCHANGED
    │       │   └── MinioXmlStorageAdapter.java
    │       ├── messaging/                  # UNCHANGED
    │       │   ├── OutboxSagaReplyAdapter.java
    │       │   └── OutboxXmlSignedEventAdapter.java
    │       └── xml/                        # NEW — consolidates embedder + util
    │           ├── XadesSignatureEmbedder.java  # MOVED from infrastructure/embedder/; implements XadesEmbeddingPort
    │           └── SecureXmlParser.java         # MOVED from infrastructure/util/
    ├── config/
    │   ├── feign/                          # NEW sub-package
    │   │   ├── FeignConfig.java
    │   │   └── CSCErrorDecoder.java
    │   ├── minio/                          # NEW sub-package
    │   │   └── MinioConfig.java
    │   └── outbox/                         # NEW sub-package
    │       └── OutboxConfig.java
    └── persistence/                        # UNCHANGED
        ├── SignedXmlDocumentEntity.java
        ├── SignedXmlDocumentMapper.java
        ├── SignedXmlDocumentRepositoryAdapter.java
        ├── JpaSignedXmlDocumentRepository.java
        └── outbox/
            ├── OutboxEventEntity.java
            ├── JpaOutboxEventRepository.java
            └── SpringDataOutboxRepository.java
    # infrastructure/storage/  DELETED (MinioStorageService was legacy/unused)
    # infrastructure/embedder/ DELETED (moved to adapter/out/xml/)
    # infrastructure/messaging/ DELETED (moved to adapter/in/camel/)
    # infrastructure/util/     DELETED (moved to adapter/out/xml/)
    # infrastructure/client/   DELETED (moved to adapter/out/csc/)
```

---

## Component Design

### `application/usecase/` Merge

Four sources collapse into one package — only package declarations change except for `XmlSigningServiceImpl` which gains a new port injection:

- `SagaCommandPort` (interface) — `domain/port/in/` → `application/usecase/`
- `XmlSigningService` (interface) + `SigningResult` (value object) — `domain/service/` → `application/usecase/`
- `SagaCommandHandler` (implements `SagaCommandPort`) — `application/service/` → `application/usecase/`
- `XmlSigningServiceImpl` (implements `XmlSigningService`) — `application/service/` → `application/usecase/`; gains `XadesEmbeddingPort` injection (replaces direct `XadesSignatureEmbedder` dependency)

`domain/service/` retains only `DocumentTypeDetectionService` — it depends solely on `domain/model/DocumentType` and has no port dependencies, so it is a true domain service and stays.

### New `XadesEmbeddingPort`

`XmlSigningServiceImpl` calls `XadesSignatureEmbedder` (Apache Santuario) directly. Once it moves to `application/usecase/`, the direct infrastructure dependency violates the dependency rule. A thin port is extracted:

```java
// application/port/out/XadesEmbeddingPort.java
package com.wpanther.xmlsigning.application.port.out;

public interface XadesEmbeddingPort {
    byte[] embedSignature(byte[] xmlContent, byte[] signatureBytes,
                          String certificate, String documentId);
}
```

`XadesSignatureEmbedder` moves to `infrastructure/adapter/out/xml/`, adds `@Component`, and `implements XadesEmbeddingPort`. `SecureXmlParser` (used only by `XadesSignatureEmbedder`) moves alongside it — same package, so no import statement needed between them.

`XmlSigningServiceImpl` constructor gains `XadesEmbeddingPort xadesEmbeddingPort` injection; the direct `XadesSignatureEmbedder` field is replaced by this port.

### `domain/port/out/` Split

| Port | Destination | Rationale |
|---|---|---|
| `SignedXmlDocumentRepository` | `domain/repository/` | Repository — domain-owned contract |
| `CscAuthorizationPort` | `application/port/out/` | External CSC API — application concern |
| `CscSignaturePort` | `application/port/out/` | External CSC API — application concern |
| `XmlStoragePort` | `application/port/out/` | MinIO I/O — application concern |
| `XmlSignedEventPort` | `application/port/out/` | Outbox messaging — application concern |
| `SagaReplyPort` | `application/port/out/` | Outbox messaging — application concern |

### CSC Model Objects → `application/dto/csc/`

`domain/model/csc/` holds four records used as parameter/return types of `CscAuthorizationPort` and `CscSignaturePort`. Since those ports move to `application/port/out/`, the records move to `application/dto/csc/`. After the move, `domain/model/csc/` is deleted.

`CscAuthorizationAdapter` and `CscSignatureAdapter` update their imports from `domain.model.csc.*` → `application.dto.csc.*`.

### Kafka DTOs → `application/dto/event/`

All five classes in `domain/event/` extend `SagaCommand`, `SagaReply`, or `TraceEvent` — they are Kafka wire DTOs, not domain events. After move, `domain/event/` is deleted.

### CSC Infrastructure Consolidation

`infrastructure/client/csc/` (2 Feign clients + 6 DTOs) moves to `infrastructure/adapter/out/csc/client/` and `dto/`. The existing adapters `CscAuthorizationAdapter` and `CscSignatureAdapter` already live in `infrastructure/adapter/out/csc/`. After move, `infrastructure/client/` is deleted.

`XmlSigningServiceApplication` has `@EnableFeignClients` — root package scan covers the new location automatically.

### Inbound Adapter Consolidation

`CommandValidator` (Camel `Processor` for command validation) is used only by `SagaRouteConfig`. It moves to `infrastructure/adapter/in/camel/` — all inbound Camel concerns co-located. After move, `infrastructure/messaging/` is deleted.

### Legacy Deletion

`infrastructure/storage/MinioStorageService` — confirmed unused, replaced by `MinioXmlStorageAdapter`. Deleted along with its test `MinioStorageServiceTest`.

### Config Sub-Packages

| Class(es) | Sub-package | Rationale |
|---|---|---|
| `FeignConfig`, `CSCErrorDecoder` | `infrastructure/config/feign/` | Feign/circuit-breaker wiring |
| `MinioConfig` | `infrastructure/config/minio/` | S3Client bean |
| `OutboxConfig` | `infrastructure/config/outbox/` | OutboxService wiring |

---

## Dependency Rules

| Package | May import from | Must NOT import from |
|---|---|---|
| `domain/model/` | stdlib, Lombok | application/, infrastructure/ |
| `domain/repository/` | `domain/model/` | application/, infrastructure/ |
| `domain/service/` | `domain/model/` | application/, infrastructure/ |
| `application/usecase/` | `domain/`, `application/port/out/`, `application/dto/` | infrastructure/ |
| `application/port/out/` | `domain/model/`, `application/dto/` | infrastructure/ |
| `application/dto/` | stdlib, Jackson, saga-commons | domain/, infrastructure/ |
| `infrastructure/adapter/in/` | `application/usecase/`, `application/dto/` | `infrastructure/adapter/out/` directly |
| `infrastructure/adapter/out/` | `application/port/out/`, `domain/`, `application/dto/` | `infrastructure/adapter/in/` |
| `infrastructure/config/` | everything (Spring wiring — allowed) | — |

---

## Data Flow

### Inbound: Saga Command

```
saga.command.xml-signing (Kafka)
  → infrastructure/adapter/in/camel/SagaRouteConfig
      → CommandValidator (Camel Processor — validation)
  → application/usecase/SagaCommandPort
  → application/usecase/SagaCommandHandler
      ├── [TX1] SignedXmlDocumentRepositoryAdapter.save()   PENDING→SIGNING
      ├── application/usecase/XmlSigningServiceImpl.signXml()
      │     ├── application/port/out/CscAuthorizationPort   → SAD token
      │     │     ↓ CscAuthorizationAdapter
      │     │         client/CSCAuthClient → /csc/v2/oauth2/authorize
      │     ├── application/port/out/CscSignaturePort        → signed hash
      │     │     ↓ CscSignatureAdapter
      │     │         client/CSCSignatureClient → /csc/v2/signatures/signHash
      │     └── application/port/out/XadesEmbeddingPort     → XAdES-BASELINE-T XML
      │           ↓ XadesSignatureEmbedder (Apache Santuario)
      │               SecureXmlParser (XXE-safe parsing)
      ├── application/port/out/XmlStoragePort               store signed XML → URL
      │     ↓ MinioXmlStorageAdapter → MinIO S3
      └── [TX2] repository.save() SIGNING→COMPLETED
              + outbox → saga.reply.xml-signing (SUCCESS)
              + outbox → xml.signed
```

### Inbound: Compensation

```
saga.compensation.xml-signing (Kafka)
  → infrastructure/adapter/in/camel/SagaRouteConfig
  → application/usecase/SagaCommandPort
  → application/usecase/SagaCommandHandler.handleCompensation()
      ├── [TX] repository.deleteById() + flush
      ├── XmlStoragePort.delete() (best-effort)
      └── [TX] outbox → saga.reply.xml-signing (COMPENSATED)
```

### Outbound: XAdES Signing Detail

```
application/usecase/XmlSigningServiceImpl
  1. CscAuthorizationPort.authorize(CscAuthorizeCommand)
       ↓ application/dto/csc/CscAuthorizeCommand (port parameter)
       ↓ CscAuthorizationAdapter → maps → infrastructure/adapter/out/csc/dto/CSCAuthorizeRequest
       ← application/dto/csc/CscAuthorizeResult (SAD token)

  2. CscSignaturePort.signHash(CscSignHashCommand)
       ↓ application/dto/csc/CscSignHashCommand (port parameter)
       ↓ CscSignatureAdapter → maps → infrastructure/adapter/out/csc/dto/CSCSignatureRequest
       ← application/dto/csc/CscSignHashResult (signed hash + certificate)

  3. XadesEmbeddingPort.embedSignature(xmlContent, signatureBytes, certificate, documentId)
       ↓ XadesSignatureEmbedder (Apache Santuario)
           └── SecureXmlParser (XXE-safe DOM parsing)
       ← signed XML bytes (XAdES-BASELINE-T)
```

### Outbound: Reply + Notification (via Outbox)

```
application/usecase/SagaCommandHandler
  → application/port/out/SagaReplyPort
      ↓ OutboxSagaReplyAdapter → outbox_events → Debezium CDC → saga.reply.xml-signing

  → application/port/out/XmlSignedEventPort
      ↓ OutboxXmlSignedEventAdapter → outbox_events → Debezium CDC → xml.signed
```

---

## Import Mapping (Old → New)

| Old import | New import |
|---|---|
| `domain.port.in.SagaCommandPort` | `application.usecase.SagaCommandPort` |
| `domain.port.out.SignedXmlDocumentRepository` | `domain.repository.SignedXmlDocumentRepository` |
| `domain.port.out.CscAuthorizationPort` | `application.port.out.CscAuthorizationPort` |
| `domain.port.out.CscSignaturePort` | `application.port.out.CscSignaturePort` |
| `domain.port.out.XmlStoragePort` | `application.port.out.XmlStoragePort` |
| `domain.port.out.XmlSignedEventPort` | `application.port.out.XmlSignedEventPort` |
| `domain.port.out.SagaReplyPort` | `application.port.out.SagaReplyPort` |
| `domain.event.*` | `application.dto.event.*` |
| `domain.model.csc.*` | `application.dto.csc.*` |
| `domain.service.XmlSigningService` | `application.usecase.XmlSigningService` |
| `domain.service.SigningResult` | `application.usecase.SigningResult` |
| `application.service.SagaCommandHandler` | `application.usecase.SagaCommandHandler` |
| `application.service.XmlSigningServiceImpl` | `application.usecase.XmlSigningServiceImpl` |
| `infrastructure.client.csc.CSCAuthClient` | `infrastructure.adapter.out.csc.client.CSCAuthClient` |
| `infrastructure.client.csc.CSCSignatureClient` | `infrastructure.adapter.out.csc.client.CSCSignatureClient` |
| `infrastructure.client.csc.dto.*` | `infrastructure.adapter.out.csc.dto.*` |
| `infrastructure.embedder.XadesSignatureEmbedder` | `infrastructure.adapter.out.xml.XadesSignatureEmbedder` |
| `infrastructure.util.SecureXmlParser` | `infrastructure.adapter.out.xml.SecureXmlParser` |
| `infrastructure.messaging.CommandValidator` | `infrastructure.adapter.in.camel.CommandValidator` |
| `infrastructure.config.FeignConfig` | `infrastructure.config.feign.FeignConfig` |
| `infrastructure.config.CSCErrorDecoder` | `infrastructure.config.feign.CSCErrorDecoder` |
| `infrastructure.config.MinioConfig` | `infrastructure.config.minio.MinioConfig` |
| `infrastructure.config.OutboxConfig` | `infrastructure.config.outbox.OutboxConfig` |

---

## Migration Phases

| Phase | Scope | Commit message |
|---|---|---|
| 1 | Move `domain/event/` → `application/dto/event/`; move `domain/model/csc/` → `application/dto/csc/`; delete both source directories | `Move Kafka event DTOs to application/dto/event, move CSC model objects to application/dto/csc` |
| 2 | Move `SignedXmlDocumentRepository` → `domain/repository/`; move remaining `domain/port/out/` → `application/port/out/`; extract `XadesEmbeddingPort`; move `domain/port/in/` + `XmlSigningService` + `SigningResult` + `application/service/*` → `application/usecase/`; inject `XadesEmbeddingPort` into `XmlSigningServiceImpl`; delete `domain/port/`, `domain/service/{XmlSigningService,SigningResult}`, `application/service/` | `Split domain ports, extract XadesEmbeddingPort, merge into application/usecase` |
| 3 | Consolidate `infrastructure/client/csc/` → `adapter/out/csc/client/` + `dto/`; move `CommandValidator` → `adapter/in/camel/`; move `XadesSignatureEmbedder` + `SecureXmlParser` → `adapter/out/xml/`; delete `MinioStorageService` + test; delete `infrastructure/client/`, `messaging/`, `embedder/`, `util/`, `storage/` | `Consolidate infrastructure packages into adapter subdirectories, delete legacy storage` |
| 4 | Move `FeignConfig` + `CSCErrorDecoder` → `config/feign/`; `MinioConfig` → `config/minio/`; `OutboxConfig` → `config/outbox/` | `Move config classes to concern-based sub-packages` |
| 5 | Relocate test files; update JaCoCo exclusions | `Relocate test classes, update JaCoCo exclusions` |
| 6 | Final verification — `mvn verify`, confirm no old package references remain | (verification only) |

---

## Testing Strategy

### Test Relocations (Phase 5)

| Old test path | New test path |
|---|---|
| `domain/event/ProcessXmlSigningCommandTest` | `application/dto/event/` |
| `domain/event/CompensateXmlSigningCommandTest` | `application/dto/event/` |
| `domain/event/XmlSigningReplyEventTest` | `application/dto/event/` |
| `domain/event/XmlSignedEventTest` | `application/dto/event/` |
| `domain/event/XmlSigningRequestedEventTest` | `application/dto/event/` |
| `domain/model/csc/CscDomainValueObjectsTest` | `application/dto/csc/` |
| `application/service/SagaCommandHandlerTest` | `application/usecase/` |
| `application/service/XmlSigningServiceImplTest` | `application/usecase/` |
| `infrastructure/client/csc/dto/CSCDtoTest` | `infrastructure/adapter/out/csc/dto/` |
| `infrastructure/config/FeignConfigTest` | `infrastructure/config/feign/` |
| `infrastructure/config/CSCErrorDecoderTest` | `infrastructure/config/feign/` |
| `infrastructure/config/OutboxConfigTest` | `infrastructure/config/outbox/` |
| `infrastructure/embedder/XadesSignatureEmbedderTest` | `infrastructure/adapter/out/xml/` |
| `infrastructure/storage/MinioStorageServiceTest` | **DELETED** (class deleted) |

**Not moved:** `domain/model/*Test`, `domain/service/DocumentTypeDetectionServiceTest`, `infrastructure/adapter/out/csc/*Test`, `infrastructure/adapter/out/storage/*`, `infrastructure/adapter/out/messaging/*`, `infrastructure/adapter/in/camel/*`, `infrastructure/persistence/*`, `integration/*`, `XmlSigningServiceApplicationTest`.

### New Test Additions

**`XadesSignatureEmbedderTest`** (after relocation): add one assertion verifying the class implements the new port:

```java
@Test
void implementsXadesEmbeddingPort() {
    assertThat(embedder).isInstanceOf(XadesEmbeddingPort.class);
}
```

**`XmlSigningServiceImplTest`** (after relocation): replace direct `XadesSignatureEmbedder` mock with `XadesEmbeddingPort` mock:

```java
@Mock XadesEmbeddingPort xadesEmbeddingPort;
// replace: @Mock XadesSignatureEmbedder xadesSignatureEmbedder;
```

### JaCoCo Updates

- Remove stale exclusion for `infrastructure/storage/` (class deleted)
- Add config sub-package exclusions if needed: `infrastructure/config/feign/**`, `infrastructure/config/minio/**`, `infrastructure/config/outbox/**`

### Coverage Target

≥ 80% line coverage (`mvn verify`) maintained throughout all phases.

---

## Key Decisions

| Decision | Rationale |
|---|---|
| `domain/port/in/` renamed to `application/usecase/` | Canonical: inbound port interfaces and their implementations co-locate in `usecase/` |
| `XmlSigningService` moved to `application/usecase/` | It orchestrates via outbound ports; once ports move to `application/port/out/`, the service cannot remain in `domain/service/` without violating the dependency rule |
| `DocumentTypeDetectionService` stays in `domain/service/` | True domain logic — detects document type from XML namespace/root element; depends only on `domain/model/DocumentType`, no port references |
| `XadesEmbeddingPort` extracted | `XmlSigningServiceImpl` (in `application/usecase/`) cannot directly import `XadesSignatureEmbedder` (infrastructure); the port preserves the dependency rule and matches the `PadesSignatureAdapter` pattern in pdf-signing-service |
| `domain/model/csc/` → `application/dto/csc/` | CSC command/result records are port parameter types, not domain concepts; they travel with their port interfaces to `application/` |
| `infrastructure/client/csc/` consolidated into `adapter/out/csc/` | Feign clients are wire-protocol details of the CSC adapters; co-location makes each adapter self-contained |
| `XadesSignatureEmbedder` + `SecureXmlParser` → `adapter/out/xml/` | They exist solely to support XAdES embedding; `infrastructure/embedder/` and `infrastructure/util/` are orphan packages |
| `CommandValidator` → `adapter/in/camel/` | It is a Camel `Processor` used only by `SagaRouteConfig`; belongs with inbound Camel infrastructure |
| `MinioStorageService` deleted | Confirmed unused legacy class; replaced by `MinioXmlStorageAdapter` |
| `infrastructure/config/` split into feign/minio/outbox sub-packages | Groups configuration by concern; matches canonical pattern across all services |
