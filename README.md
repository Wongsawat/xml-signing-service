# XML Signing Service

A Spring Boot microservice for signing Thai e-Tax invoice XML documents using XAdES-BASELINE-T format via the CSC API v2.0. It participates in a Saga orchestration pattern coordinated by the orchestrator-service.

## Overview

This service integrates with the [eidasremotesigning](../../../eidasremotesigning) CSC API v2.0 service to provide digital signatures for XML invoices in the Thai e-Tax invoice processing pipeline.

**Port**: 8086
**Database**: PostgreSQL (`xmlsigning_db`)
**Java**: 21
**Spring Boot**: 3.2.5

## Position in Pipeline

```
Saga Orchestrator ──→ saga.command.xml-signing → [XML Signing] → saga.reply.xml-signing ──→ Orchestrator
                              ↓                         │                                    ↓
                    saga.compensation.xml-signing        │                          → PDF Generation / ebMS Sending
                         (rollback)                      │
                                                        └──→ xml.signed ──→ Notification Service
```

The service follows the **Saga Orchestration Pattern**:
- Consumes commands from orchestrator via `saga.command.xml-signing`
- Consumes compensation commands from orchestrator via `saga.compensation.xml-signing`
- Publishes replies to orchestrator via `saga.reply.xml-signing` (Transactional Outbox Pattern with Debezium CDC)
- Publishes `XmlSignedEvent` to `xml.signed` topic for notification-service (via Transactional Outbox)

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

# Run locally
export DB_HOST=localhost
export DB_NAME=xmlsigning_db
export KAFKA_BROKERS=localhost:9092
export CSC_SERVICE_URL=http://localhost:9000
export CSC_CLIENT_ID=etax-invoice-service
export CSC_CREDENTIAL_ID=default-credential
mvn spring-boot:run

# Run with Docker test environment (different ports)
DB_PORT=5433 KAFKA_BROKERS=localhost:9093 mvn spring-boot:run

# Run tests
mvn test

# Database migrations
mvn flyway:migrate
mvn flyway:info
```

## Configuration

Key environment variables:

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
| `CSC_PIN` | 1234 | CSC PIN for signHash endpoint authentication |
| `CSC_HASH_ALGORITHM` | SHA-256withRSA | Hash algorithm for signing |
| `CSC_DIGEST_ALGORITHM` | SHA256 | Digest algorithm for local hash computation |
| `CSC_SIGNATURE_LEVEL` | XAdES-BASELINE-T | XAdES signature level |
| `SIGNING_MAX_RETRIES` | 3 | Maximum signing retry attempts |
| `SIGNING_TIMEOUT_SECONDS` | 30 | Signing operation timeout |
| `EUREKA_URL` | http://localhost:8761/eureka/ | Service registry URL |

## Architecture

### Domain Model

**SignedXmlDocument** (Aggregate Root)
- State machine: `PENDING → SIGNING → COMPLETED` (or `FAILED`)
- Tracks signing status, retry count, and error messages
- Uses manual Builder pattern with constructor validation
- Value object `SignedXmlDocumentId` (Java record with factory methods)

**Integration Events** (saga-commons)
- Domain events extend `saga-commons IntegrationEvent` base class
- Provides standard event metadata: `eventId`, `occurredAt`, `eventType`, `version`
- Supports consistent event handling across microservices

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

**Consumes**: `saga.compensation.xml-signing`
```json
{
  "sagaId": "uuid",
  "sagaStep": "xml-signing",
  "correlationId": "uuid",
  "stepToCompensate": "xml-signing",
  "documentId": "uuid",
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

### Signing Process (signHash Pattern)

1. **Compute Digest** - Calculate SHA-256 hash of XML locally
2. **Sign Hash** - Call CSC API (`/csc/v2/signatures/signHash`) with digest and PIN (no SAD token needed)
3. **Sign Hash** - Call CSC API (`/csc/v2/signatures/signHash`) with digest only
4. **Embed Signature** - Use `XadesSignatureEmbedder` to embed signature into XML as XAdES-BASELINE-T with actual digest value
5. **Embed Signature** - Use `XadesSignatureEmbedder` to embed signature into XML as XAdES-BASELINE-T
6. **Notify** - Write `XmlSignedEvent` to outbox with topic `xml.signed` (for notification-service)
7. **Notify** - Write `XmlSignedEvent` to outbox with topic `xml.signed` (for notification-service)
8. **Publish Reply** - Write `saga.reply.xml-signing` event to outbox table (Debezium CDC delivers to Kafka)

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

### Error Handling

- **Retry Logic**: Up to 3 attempts (`SIGNING_MAX_RETRIES`) for transient errors
- **Circuit Breaker**: Protects against CSC service failures (50% failure rate threshold, 60s cooldown, 10-call sliding window)
- **Feign Retry**: 3 attempts with 100ms-1000ms backoff
- **Camel DLQ**: Automatic redelivery with exponential backoff (1s → 2s → 4s, max 10s), then routes to Dead Letter Queue
- **State Tracking**: All failures logged in database with error messages

### Resilience Patterns

| Pattern | Configuration |
|----------|--------------|
| **Circuit Breaker** (Resilience4j) | Sliding window: 10 calls, failure threshold: 50%, open duration: 60s, half-open calls: 3 |
| **Feign Retry** | 3 attempts, 100ms-1000ms backoff |
| **Application Retry** | `SIGNING_MAX_RETRIES` (default 3) on domain model |
| **Camel DLQ** | 3 redeliveries with exponential backoff, then DLQ topic |
| **Timeouts** | Connect: 10s, Read: 30s, Circuit breaker: 30s |

## Database Schema

Flyway migrations in `src/main/resources/db/migration/`:

### signed_xml_documents

```sql
signed_xml_documents (
  id UUID PRIMARY KEY,
  invoice_id VARCHAR(100) UNIQUE,
  invoice_number VARCHAR(50),
  document_type VARCHAR(50) DEFAULT 'TAX_INVOICE',
  original_xml TEXT,
  signed_xml TEXT,
  transaction_id VARCHAR(100),
  certificate TEXT,
  signature_level VARCHAR(50),
  status VARCHAR(20),  -- PENDING, SIGNING, COMPLETED, FAILED
  error_message TEXT,
  retry_count INTEGER,
  created_at TIMESTAMP,
  completed_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### outbox_events

```sql
outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR(100),
  aggregate_id VARCHAR(100),
  event_type VARCHAR(100),
  payload TEXT,
  topic VARCHAR(255),
  partition_key VARCHAR(255),
  headers TEXT,
  status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
  retry_count INTEGER,
  error_message VARCHAR(1000),
  created_at TIMESTAMP,
  published_at TIMESTAMP
)
```

**Outbox Pattern**: Events are written to `outbox_events` within the same transaction as the domain change. Debezium CDC reads the table via PostgreSQL logical replication and publishes to Kafka, ensuring exactly-once delivery.

**Indexes**:
- `signed_xml_documents`: invoice_id (unique), invoice_number, status, transaction_id, document_type
- `outbox_events`: status, created_at, partial index on created_at for PENDING events (optimized for Debezium polling)

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
- `resilience4j.circuitbreaker.state` - Circuit breaker state
- `kafka.consumer.records-consumed-total` - Kafka consumer metrics

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
- Check TSP (Timestamp Authority) reachability for BASELINE-T

**Camel Jackson fails to deserialize `java.time.Instant`**
- Events extending `IntegrationEvent` have `occurredAt` (Instant type)
- Camel's `.unmarshal().json(JsonLibrary.Jackson, ...)` creates its own ObjectMapper without `JavaTimeModule`
- Symptoms: `InvalidDefinitionException: Java 8 date/time type java.time.Instant not supported by default`
- Fix: Set `camel.dataformat.jackson.auto-discover-object-mapper: true` in `application.yml` (already configured)

**Outbox events not reaching Kafka**
- Check Debezium connector is running and configured for `outbox_events` table
- Verify PostgreSQL logical replication is enabled (`wal_level=logical`)

## Technology Stack

- Java 21
- Spring Boot 3.2.5
- Spring Cloud 2023.0.1
- Apache Camel 4.14.4 (Kafka integration)
- Apache Santuario 4.0.2 (XML DSig/XAdES signatures)
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
├── repository/    # Domain repository interfaces
├── service/      # Domain service interfaces
└── event/        # Saga commands, replies, XmlSignedEvent

application/
└── service/      # Saga command handler

infrastructure/
├── persistence/   # JPA entities, repositories, mappers, outbox
├── client/        # CSC API implementation (signHash endpoint)
├── embedder/      # XAdES-BASELINE-T signature embedding
├── messaging/     # Saga reply publisher, event publisher (XmlSignedEvent)
└── config/        # Camel routes, Feign, Outbox configuration
```

### Development Notes

- **Domain changes**: Update `SignedXmlDocument` aggregate first (uses manual Builder, not Lombok)
- **Saga commands**: Extend `IntegrationEvent`, use `@JsonCreator` with two constructors (all-args for deserialization, convenience for testing)
- **Saga replies**: Extend `SagaReply`, use factory methods (`success()`, `failure()`, `compensated()`)
- **CSC API changes**: Update DTOs in `infrastructure/client/csc/dto/` and `XmlSigningServiceImpl`
- **Signature embedding**: `XadesSignatureEmbedder` handles XAdES-BASELINE-T signature embedding
- **New document types**: Add to `DocumentType` enum with namespace URI
- **Compensation**: Implement delete logic in `SagaCommandHandler.handleCompensation()`, ensure idempotency
