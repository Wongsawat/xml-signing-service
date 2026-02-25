# XML Signing Service

A Spring Boot 3.2.5 microservice for signing Thai e-Tax invoice XML documents using XAdES-BASELINE-T format via the CSC API v2.0 signHash endpoint with SAD token authentication. This service participates in a Saga orchestration pattern coordinated by the orchestrator-service.

## Overview

This service integrates with the [eidasremotesigning](../../../eidasremotesigning) CSC API v2.0 service to provide digital signatures for XML invoices in the Thai e-Tax invoice processing pipeline.

**Port**: 8086
**Database**: PostgreSQL (`xmlsigning_db`)
**Java**: 21
**Spring Boot**: 3.2.5
**Architecture**: Hexagonal Architecture (Domain-Driven Design with Ports)

## Key Features

- **XAdES-BASELINE-T Signing**: Full compliance with ETSI EN 319 132-1
- **SAD Token Authentication**: Secure, short-lived token-based auth (replaces deprecated PIN-based auth)
- **XXS Protection**: Comprehensive XML External Entity attack prevention (OWASP-compliant)
- **Saga Orchestration**: Transactional Outbox Pattern with Debezium CDC for exactly-once delivery
- **Circuit Breaker**: Resilience4j protection against CSC service failures
- **Hexagonal Ports**: Clean separation between domain logic and infrastructure adapters

## Position in Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPLETE SAGA ORCHESTRATION FLOW (Tax Invoice)                                    │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

1. DOCUMENT INTAKE → 2. PROCESS_TAX_INVOICE → 3. SIGN_XML → 4. SIGNEDXML_STORAGE
                                                   ↓
[Orchestrator:8093] → saga.command.xml-signing → [XML Signing:8086]
                                                ↓
                              saga.reply.xml-signing → [Orchestrator]

In parallel with saga reply, XML Signing also publishes:
  xml.signed → Notification Service (for user notifications)
```

The service follows the **Saga Orchestration Pattern**:
- **Consumes**: `saga.command.xml-signing` commands from orchestrator
- **Consumes**: `saga.compensation.xml-signing` compensation commands from orchestrator
- **Produces**: `saga.reply.xml-signing` replies via Transactional Outbox Pattern with Debezium CDC
- **Produces**: `xml.signed` events for notification-service (via Transactional Outbox)

## Local Development Ports

| Service | Default Port | Environment Variable | Notes |
|---------|--------------|---------------------|-------|
| xml-signing-service | 8086 | `SERVER_PORT` | This service |
| PostgreSQL | 5432 | `DB_HOST`, `DB_PORT` | xmlsigning_db |
| Kafka | 9092 | `KAFKA_BROKERS` | Message broker |
| eidasremotesigning (CSC) | 9000 | `CSC_SERVICE_URL` | Digital signature service |
| MinIO | 9001 | `MINIO_ENDPOINT` | S3-compatible storage |

**Port Conflict Note**: By default, MinIO uses port 9001 to avoid conflicts with eidasremotesigning (port 9000). Override with `MINIO_ENDPOINT` if needed.

## Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL 16+ with database `xmlsigning_db`
- Kafka running on `localhost:9092`
- **eidasremotesigning** service running on port 9000 (required for signing operations)
- **saga-commons** library installed: `cd ../../../saga-commons && mvn clean install`
- **teda** library installed: `cd ../../../teda && mvn clean install`
- Eureka service registry (optional)
- Debezium (for integration tests with CDC)

## Build and Run

```bash
# Build
mvn clean package

# Run locally (development profile - DEBUG logging)
mvn spring-boot:run

# Run with production profile (INFO/WARN logging)
mvn spring-boot:run --spring.profiles.active=prod

# Run with Docker test environment (different ports)
DB_PORT=5433 KAFKA_BROKERS=localhost:9093 mvn spring-boot:run

# Run tests
mvn test

# Database migrations
mvn flyway:migrate
mvn flyway:info
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|-----------|----------|-------------|
| `KAFKA_BROKERS` | localhost:9092 | Kafka bootstrap servers |
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | xmlsigning_db | Database name |
| `DB_USERNAME` | postgres | Database username |
| `DB_PASSWORD` | postgres | Database password |
| `CSC_SERVICE_URL` | http://localhost:9000 | eIDAS signing service URL |
| `CSC_CLIENT_ID` | etax-invoice-service | CSC client identifier |
| `CSC_CREDENTIAL_ID` | default-credential | CSC credential identifier |
| `CSC_HASH_ALGORITHM` | SHA256withRSA | Hash algorithm for signing |
| `CSC_DIGEST_ALGORITHM` | SHA256 | Digest algorithm for local hash computation |
| `CSC_SIGNATURE_LEVEL` | XAdES-BASELINE-T | XAdES signature level |
| `MINIO_ENDPOINT` | http://localhost:9001 | MinIO/S3 endpoint |
| `MINIO_ACCESS_KEY` | minioadmin | MinIO access key |
| `MINIO_SECRET_KEY` | minioadmin | MinIO secret key |
| `MINIO_BUCKET_NAME` | signed-xml-documents | MinIO bucket name |
| `SIGNING_MAX_RETRIES` | 3 | Maximum signing retry attempts |
| `SIGNING_TIMEOUT_SECONDS` | 30 | Signing operation timeout |
| `SIGNING_MAX_ALLOWED_RETRIES` | 10 | Maximum allowed retry limit |
| `SIGNING_MAX_ALLOWED_TIMEOUT_SECONDS` | 300 | Maximum allowed timeout (5 min) |
| `SIGNING_MIN_ALLOWED_TIMEOUT_SECONDS` | 5 | Minimum allowed timeout (5 sec) |
| `EUREKA_URL` | http://localhost:8761/eureka/ | Service registry URL |

### Profiles

- **default** (development): DEBUG logging for `com.wpanther.xmlsigning`
- **prod**: Production-optimized logging (INFO/WARN levels, reduced noise)

Activate with: `--spring.profiles.active=prod`

## Architecture

### Hexagonal Ports (Domain-Driven Design)

The service uses hexagonal architecture to isolate domain logic from infrastructure:

```
domain/
├── port/                    # Domain ports (interfaces)
│   ├── CscAuthorizationPort     # CSC authorize endpoint
│   └── CscSignaturePort        # CSC signHash endpoint
├── model/                    # Aggregate roots, value objects
│   ├── SignedXmlDocument       # Aggregate root with manual Builder
│   ├── SignedXmlDocumentId     # Value object (Java record)
│   ├── DocumentType            # Enum of 6 Thai e-Tax document types
│   └── SigningStatus           # State machine enum
├── repository/               # Repository interfaces
├── service/                  # Domain service interfaces
└── event/                    # Integration events
    ├── ProcessXmlSigningCommand
    ├── CompensateXmlSigningCommand
    ├── XmlSigningReplyEvent
    └── XmlSignedEvent

infrastructure/
├── client/csc/              # Hexagonal port implementations
│   ├── CSCAuthClient         # Feign client for authorize endpoint
│   └── CSCSignatureClient     # Feign client for signHash endpoint
├── embedder/                 # XAdES-BASELINE-T signature embedding
│   └── XadesSignatureEmbedder # Apache Santuario 4.0.4
├── messaging/                # Outbox pattern publishers
│   ├── SagaReplyPublisher    # saga.reply.xml-signing
│   └── EventPublisher        # xml.signed events
├── persistence/              # JPA entities, mappers
├── storage/                  # MinIO integration
└── config/                   # Camel routes, Feign, circuit breaker

application/
└── service/                  # Use case orchestration
    └── SagaCommandHandler    # Handles saga commands
```

### Domain Model

**SignedXmlDocument** (Aggregate Root)
- State machine: `PENDING → SIGNING → COMPLETED` (or `FAILED`)
- Tracks signing status, retry count, transaction ID, certificate
- Uses manual Builder pattern with constructor validation
- Value object `SignedXmlDocumentId` (Java record with factory methods: `create()`, `from()`)

**DocumentType** (Enum)
- 6 Thai e-Tax document types with namespace URIs
- `TAX_INVOICE`, `RECEIPT`, `INVOICE`, `DEBIT_CREDIT_NOTE`, `CANCELLATION_NOTE`, `ABBREVIATED_TAX_INVOICE`

### CSC API Integration (signHash Pattern)

**Authentication Flow:**
1. **Authorize** - `POST /csc/v2/credentials/authorize` → returns SAD token (~15 min validity)
2. **Sign Hash** - `POST /csc/v2/signatures/signHash` with SAD token → returns raw signature

**Hexagonal Ports:**
- `CscAuthorizationPort` - Domain interface for authorize operations
- `CscSignaturePort` - Domain interface for signHash operations
- Implemented by `CSCAuthClient` and `CSCSignatureClient` (Feign clients)

**Error Handling:**
- `CscAuthorizationException` - Authorization failures (includes clientId, credentialId for debugging)
- `CscSignatureException` - Signing failures (includes transactionId for debugging)
- Typed exceptions preserve circuit breaker behavior

### XXS Protection

XML parsing is secured against XML External Entity (XXE) attacks following OWASP recommendations:

```java
// XXE Protection features enabled in createSecureDocumentBuilderFactory():
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
dbf.setExpandEntityReferences(false);
dbf.setXIncludeAware(false);
```

### Saga Event Flow

**Consumes**: `saga.command.xml-signing`
```json
{
  "sagaId": "uuid",
  "sagaStep": "xml-signing",
  "correlationId": "uuid",
  "documentId": "uuid",
  "xmlContent": "<xml>...</xml>",
  "invoiceNumber": "INV-001",
  "documentType": "TAX_INVOICE"
}
```

**Produces**: `saga.reply.xml-signing` (via Transactional Outbox Pattern with Debezium CDC)
```json
{
  "sagaId": "uuid",
  "sagaStep": "xml-signing",
  "correlationId": "uuid",
  "replyStatus": "SUCCESS",
  "errorMessage": null
}
```

**Produces**: `xml.signed` (via Transactional Outbox Pattern — consumed by notification-service)
```json
{
  "invoiceId": "uuid",
  "invoiceNumber": "INV-001",
  "documentType": "TAX_INVOICE",
  "correlationId": "uuid"
}
```

### Document Type Detection

The service supports 6 Thai e-Tax document types:

| Document Type | Namespace URI |
|---------------|---------------|
| TAX_INVOICE | `urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2` |
| RECEIPT | `urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2` |
| INVOICE | `urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2` |
| DEBIT_CREDIT_NOTE | `urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2` |
| CANCELLATION_NOTE | `urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2` |
| ABBREVIATED_TAX_INVOICE | `urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2` |

**Detection strategies** (in order of precedence):
1. From `documentType` field in saga command (preferred)
2. From XML namespace URI via `DocumentTypeDetectionService`
3. From XML root element name (fallback)

### Error Handling & Resilience

| Pattern | Configuration |
|----------|--------------|
| **Application Retry** | `SIGNING_MAX_RETRIES` (default 3) on domain model |
| **Circuit Breaker** (Resilience4j) | Sliding window: 10 calls, failure threshold: 50%, open duration: 60s |
| **Feign Timeouts** | Connect: 10s, Read: 30s (configured via YAML) |
| **Feign Retry** | Disabled for signHash (SAD tokens are single-use) |
| **Camel DLQ** | 3 redeliveries with exponential backoff, then DLQ topic |

**CSC Error Handling:**
- Authorization errors: `CscAuthorizationException` (includes clientId, credentialId)
- Signature errors: `CscSignatureException` (includes transactionId)
- HTTP 429 (rate limit): Retry with backoff
- HTTP 5xx (service unavailable): Retry with backoff
- HTTP 401/403 (auth/forbidden): No retry (credential issues)

### Outbox Pattern

Events are written to `outbox_events` within the same transaction as the domain change. Debezium CDC reads the table via PostgreSQL logical replication and publishes to Kafka, ensuring exactly-once delivery.

**Transaction Strategy:**
1. Upload original XML to MinIO (no transaction)
2. TX1: Persist SIGNING state (short-lived)
3. Sign XML via CSC API + upload signed XML to MinIO (no transaction)
4. TX2: Persist COMPLETED + write both outbox events atomically

## Database Schema

Flyway migrations in `src/main/resources/db/migration/`:

### signed_xml_documents

```sql
signed_xml_documents (
  id UUID PRIMARY KEY,
  invoice_id VARCHAR(100) UNIQUE NOT NULL,
  invoice_number VARCHAR(50),
  document_type VARCHAR(50) DEFAULT 'TAX_INVOICE',
  original_xml_url VARCHAR(500),
  signed_xml_url VARCHAR(500),
  signed_xml_size BIGINT,
  transaction_id VARCHAR(100),
  certificate TEXT,
  signature_level VARCHAR(50),
  status VARCHAR(20),  -- PENDING, SIGNING, COMPLETED, FAILED
  error_message TEXT,
  retry_count INTEGER DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

**Note**: `original_xml` and `signed_xml` columns removed - documents are stored in MinIO instead to keep database tables lightweight.

### outbox_events

```sql
outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(100),
  aggregate_id VARCHAR(100),
  event_type VARCHAR(100),
  payload JSONB,
  topic VARCHAR(255),
  partition_key VARCHAR(255),
  headers JSONB,
  status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
  retry_count INTEGER DEFAULT 0,
  error_message VARCHAR(1000),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP
)
```

**Partial Index** (optimized for Debezium polling):
```sql
CREATE INDEX idx_outbox_debezium ON outbox_events(created_at)
WHERE status = 'PENDING';
```

## Integration with eidasremotesigning

This service requires [eidasremotesigning](../../../eidasremotesigning) service to be running and properly configured with:

- Valid credentials (client ID and credential ID)
- Certificate for signing
- TSP (Timestamp Authority) for XAdES-BASELINE-T signatures

See [eidasremotesigning/README.md](../../../eidasremotesigning/README.md) for setup instructions.

## Monitoring

Actuator endpoints available at:
- Health: `http://localhost:8086/actuator/health`
- Metrics: `http://localhost:8086/actuator/metrics`
- Prometheus: `http://localhost:8086/actuator/prometheus`
- Camel Routes: `http://localhost:8086/actuator/camelroutes`

Key metrics:
- `resilience4j.circuitbreaker.state` - Circuit breaker state (CLOSED, OPEN, HALF_OPEN)
- `kafka.consumer.records-consumed-total` - Kafka consumer metrics
- Custom business metrics in `CircuitBreakerMetricsService`

## Troubleshooting

### Common Issues

**Connection refused to CSC service**
- Ensure eidasremotesigning is running on port 9000
- Check `CSC_SERVICE_URL` configuration

**Kafka consumer not receiving messages**
- Verify Kafka is running
- Check Camel routes: `curl http://localhost:8086/actuator/camelroutes`
- Review Camel logs with `org.apache.camel.component.kafka=DEBUG`

**Database connection errors**
- Ensure PostgreSQL database `xmlsigning_db` exists
- Run Flyway migrations: `mvn flyway:migrate`

**Max retries exceeded**
- Check eidasremotesigning service logs for signing errors
- Verify credential ID and client ID are correct
- Ensure certificate is valid and not expired
- Check TSP (Timestamp Authority) reachability

**Camel Jackson fails to deserialize `java.time.Instant`**
- Ensure `camel.dataformat.jackson.auto-discover-object-mapper: true` in `application.yml` (already configured)

**Outbox events not reaching Kafka**
- Check Debezium connector is running and configured for `outbox_events` table
- Verify PostgreSQL logical replication is enabled (`wal_level=logical`)

**CSC API returns typed exceptions instead of generic RuntimeException**
- This is intentional - domain exceptions (`CscAuthorizationException`, `CscSignatureException`) include debugging context
- Circuit breaker still triggers correctly (catches `Exception` broadly)

## Technology Stack

- Java 21
- Spring Boot 3.2.5
- Spring Cloud 2023.0.1
- Apache Camel 4.14.4 (Kafka integration)
- Apache Santuario 4.0.4 (XML DSig/XAdES signatures)
- Spring Cloud OpenFeign (CSC API client)
- saga-commons (integration events, outbox pattern)
- PostgreSQL 16
- Flyway (database migrations)
- Resilience4j (circuit breaker)
- Lombok + MapStruct (code generation)

## Development

### Running Tests

```bash
# Unit tests (H2 in-memory database)
mvn test

# Tests with coverage verification
mvn verify

# Integration tests (requires external containers)
cd ../../
./scripts/test-containers-start.sh --with-debezium --auto-deploy-connectors
cd services/xml-signing-service
mvn test -Pintegration
```

**Unit tests** use H2 in PostgreSQL compatibility mode. No external services required (CSC API mocked via WireMock).

**Integration tests** require external containers (PostgreSQL:5433, Kafka:9093, Debezium:8083):
- **Consumer tests**: End-to-end Kafka consumer → signing → outbox flow
- **CDC tests**: Verify Debezium CDC reads outbox events and publishes to Kafka

JaCoCo coverage reports: `target/site/jacoco/index.html`

Minimum coverage requirement: 80% per package

### Code Organization

```
domain/
├── model/       # Aggregate roots, value objects, enums
├── port/        # Hexagonal ports (domain interfaces)
├── repository/  # Repository interfaces
├── service/     # Domain service interfaces
└── event/       # Saga commands, replies, XmlSignedEvent

application/
└── service/     # Saga command handler, use cases

infrastructure/
├── client/      # Hexagonal port implementations (CSC Feign clients)
├── embedder/    # XAdES-BASELINE-T signature embedding
├── messaging/   # Outbox pattern publishers
├── persistence/  # JPA entities, repositories, MapStruct mappers
├── storage/     # MinIO S3 storage adapter
└── config/      # Camel routes, Feign configuration, circuit breaker
```

### Development Notes

- **Domain changes**: Update `SignedXmlDocument` aggregate first (uses manual Builder, not Lombok)
- **Saga commands**: Extend `IntegrationEvent`, use `@JsonCreator` with two constructors
- **Saga replies**: Extend `SagaReply`, use factory methods (`success()`, `failure()`, `compensated()`)
- **CSC API changes**: Update DTOs in `infrastructure/client/csc/dto/` and `XmlSigningServiceImpl`
  - Uses SAD token authentication: `CSCAuthClient.authorize()` → `CSCSignatureClient.signHash(SAD)`
- **Signature embedding**: `XadesSignatureEmbedder` handles XAdES-BASELINE-T signature embedding with XXS protection
- **New document types**: Add to `DocumentType` enum with namespace URI
- **Compensation**: Implement delete logic in `SagaCommandHandler.handleCompensation()`, ensure idempotency
- **Feign timeout changes**: Update `spring.cloud.openfeign.client.config.default` in `application.yml`

## Migration Notes

### Deprecated signDocument Endpoint (Removed in v1.1.0)

The deprecated `CSCApiClient` with the `signDocument` endpoint has been removed:

**Old Pattern (Removed):**
```java
// Old: Full document upload
CSCApiClient.signDocument(document) → returns signed XML
```

**New Pattern (Current):**
```java
// New: Hash-only upload with SAD token
CSCAuthClient.authorize() → SAD token
CSCSignatureClient.signHash(SAD, hash) → raw signature
XadesSignatureEmbedder.embedSignature() → signed XML
```

Benefits:
- More efficient (sends hash instead of full document)
- Better security (SAD tokens are single-use, short-lived)
- Separation of concerns (signing vs embedding)
