# XML Signing Service

A Spring Boot microservice for signing Thai e-Tax invoice XML documents using XAdES-BASELINE-T format before they are embedded in PDF documents.

## Overview

This service integrates with the [eidasremotesigning](../../../eidasremotesigning) CSC API v2.0 service to provide digital signatures for XML invoices in the Thai e-Tax invoice processing pipeline.

**Port**: 8086
**Database**: PostgreSQL (`xmlsigning_db`)

## Position in Pipeline

```
Invoice Processing ────────┐
                             ├──→ xml.signing.requested → [XML Signing Service] → xml.signed.* → PDF Generation
Tax Invoice Processing ────┘
```

The service routes signed documents to type-specific Kafka topics based on document type:
- `xml.signed.tax-invoice` - Tax invoices
- `xml.signed.receipt` - Receipts
- `xml.signed.invoice` - Invoices
- `xml.signed.debit-credit-note` - Debit/credit notes
- `xml.signed.cancellation-note` - Cancellation notes
- `xml.signed.abbreviated-tax-invoice` - Abbreviated tax invoices

## Prerequisites

- Java 21+
- Maven 3.6+
- PostgreSQL 16+ with database `xmlsigning_db`
- Kafka running on `localhost:9092`
- eidasremotesigning service running on port 9000
- saga-commons library installed: `cd ../../saga-commons && mvn clean install`
- Eureka service registry (optional)

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

# Run tests
mvn test

# Database migrations
mvn flyway:migrate
mvn flyway:info
```

## Configuration

Key environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_NAME` | xmlsigning_db | Database name |
| `DB_USERNAME` | postgres | Database username |
| `DB_PASSWORD` | postgres | Database password |
| `KAFKA_BROKERS` | localhost:9092 | Kafka bootstrap servers |
| `CSC_SERVICE_URL` | http://localhost:9000 | eIDAS signing service URL |
| `CSC_CLIENT_ID` | etax-invoice-service | CSC client ID |
| `CSC_CREDENTIAL_ID` | default-credential | CSC credential ID |
| `CSC_HASH_ALGORITHM` | SHA256 | Hash algorithm |
| `CSC_SIGNATURE_LEVEL` | XAdES-BASELINE-T | XAdES signature level |
| `SIGNING_MAX_RETRIES` | 3 | Maximum retry attempts |
| `EUREKA_URL` | http://localhost:8761/eureka/ | Service registry URL |

## Architecture

### Domain Model

**SignedXmlDocument** (Aggregate Root)
- State machine: `PENDING → SIGNING → COMPLETED/FAILED`
- Tracks signing status, retry count, and error messages

**Integration Events**
- Domain events extend `saga-commons IntegrationEvent` base class
- Provides standard event metadata: `eventId`, `occurredAt`, `eventType`, `version`
- Supports consistent event handling across microservices

### Event Flow

**Consumes**: `xml.signing.requested`
```json
{
  "invoiceId": "uuid",
  "invoiceNumber": "INV-001",
  "xmlContent": "<xml>...</xml>",
  "invoiceDataJson": "{...}",
  "documentType": "TAX_INVOICE",
  "correlationId": "uuid"
}
```

**Produces**: `xml.signed.[document-type]` (type-specific topics)
```json
{
  "documentId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-001",
  "signedXmlContent": "<xml with signature>...</xml>",
  "invoiceDataJson": "{...}",
  "transactionId": "TXN-...",
  "certificate": "...",
  "signatureLevel": "XAdES-BASELINE-T",
  "documentType": "TAX_INVOICE",
  "correlationId": "uuid"
}
```

### Signing Process

1. **Authorize** - Request SAD token from CSC API (`/csc/v2/credentials/authorize`)
2. **Encode** - Base64 encode XML content
3. **Calculate Digest** - SHA-256 hash of XML
4. **Sign** - Call CSC API (`/csc/v2/signatures/signDocument`) with XAdES attributes
5. **Decode** - Base64 decode signed XML
6. **Publish** - Send `xml.signed.[document-type]` event to appropriate type-specific topic

### Document Type Detection

The service supports 6 Thai e-Tax document types and routes to appropriate Kafka topics:

| Document Type | Namespace URI | Kafka Topic |
|---------------|---------------|-------------|
| TAX_INVOICE | `urn:etda:uncefact:data:standard:TaxInvoice_CrossIndustryInvoice:2` | `xml.signed.tax-invoice` |
| RECEIPT | `urn:etda:uncefact:data:standard:Receipt_CrossIndustryInvoice:2` | `xml.signed.receipt` |
| INVOICE | `urn:etda:uncefact:data:standard:Invoice_CrossIndustryInvoice:2` | `xml.signed.invoice` |
| DEBIT_CREDIT_NOTE | `urn:etda:uncefact:data:standard:DebitCreditNote_CrossIndustryInvoice:2` | `xml.signed.debit-credit-note` |
| CANCELLATION_NOTE | `urn:etda:uncefact:data:standard:CancellationNote_CrossIndustryInvoice:2` | `xml.signed.cancellation-note` |
| ABBREVIATED_TAX_INVOICE | `urn:etda:uncefact:data:standard:AbbreviatedTaxInvoice_CrossIndustryInvoice:2` | `xml.signed.abbreviated-tax-invoice` |

**Detection strategies** (in order of precedence):
1. From `documentType` field in the incoming event (preferred)
2. From XML namespace URI (automatic detection)
3. From XML root element name (fallback)

### Error Handling

- **Retry Logic**: Up to 3 attempts for transient errors
- **Circuit Breaker**: Protects against CSC service failures (50% failure rate threshold, 60s cooldown)
- **Camel DLQ**: Automatic redelivery with exponential backoff (1s → 10s), then routes to Dead Letter Queue
- **State Tracking**: All failures logged in database with error messages

## Database Schema

```sql
signed_xml_documents (
  id UUID PRIMARY KEY,
  invoice_id VARCHAR(100) UNIQUE,
  invoice_number VARCHAR(50),
  document_type VARCHAR(50),
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

## Integration with eidasremotesigning

This service requires the [eidasremotesigning](../../../eidasremotesigning) service to be running and properly configured with:

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

## Troubleshooting

### Common Issues

**Connection refused to CSC service**
- Ensure eidasremotesigning is running on port 9000
- Check `CSC_SERVICE_URL` configuration

**Kafka consumer not receiving messages**
- Verify Kafka is running and topic `xml.signing.requested` exists
- Check Camel routes: `curl http://localhost:8086/actuator/camelroutes`
- Review Camel logs with `org.apache.camel.component.kafka=DEBUG`

**Database connection errors**
- Ensure PostgreSQL database `xmlsigning_db` exists
- Run Flyway migrations: `mvn flyway:migrate`

**Max retries exceeded**
- Check CSC service logs for signing errors
- Verify credential ID and client ID are correct
- Ensure certificate is valid and not expired

## Technology Stack

- Java 21
- Spring Boot 3.2.5
- Apache Camel 4.14.4 (Kafka integration)
- Spring Cloud OpenFeign (CSC API client)
- saga-commons (integration events)
- PostgreSQL 16
- Flyway (database migrations)
- Resilience4j (circuit breaker)
- Lombok + MapStruct (code generation)

## Development

Run tests with coverage:
```bash
mvn verify
```

JaCoCo coverage reports: `target/site/jacoco/index.html`

Minimum coverage requirement: 80% per package
