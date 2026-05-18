# Fix Test Config Inconsistencies — Design Spec

## Context

`application-cdc-test.yml` uses different environment variable names than `application-consumer-test.yml` and what `test-containers-start.sh` generates in `set-env.sh`. This causes integration tests against Docker containers to always use defaults instead of the container ports.

Additionally, `application-consumer-test.yml` and `application-cdc-test.yml` are missing the `pin` field that `application-test.yml` has.

## Problem Details

| Config | DB URL | Kafka | pin |
|--------|--------|-------|-----|
| `application-consumer-test.yml` | `${DB_HOST:localhost}:${DB_PORT:5433}` ✓ | `${KAFKA_BROKERS:localhost:9093}` ✓ | _(missing)_ |
| `application-cdc-test.yml` | `${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}` ✗ | `${KAFKA_BOOTSTRAP_SERVERS:localhost:9093}` ✗ | _(missing)_ |
| `application-test.yml` | (H2, not applicable) | (not applicable) | `pin: test-pin-1234` ✓ |
| `set-env.sh` (generated) | `DB_HOST`, `DB_PORT` ✓ | `KAFKA_BROKERS` ✓ | _(not set)_ |

Root cause: `test-containers-start.sh` generates `set-env.sh` with `DB_HOST`/`DB_PORT` and `KAFKA_BROKERS`. Only `application-cdc-test.yml` uses different variable names that always fall through to defaults.

## Solution

Three targeted YAML edits — no Java code changes.

### 1. Normalize `application-cdc-test.yml` DB and Kafka env vars

```yaml
# Before
datasource:
  url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5433}/${DB_NAME:xmlsigning_db}

app:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9093}

# After
datasource:
  url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5433}/${DB_NAME:xmlsigning_db}

app:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9093}
```

### 2. Add `pin` to `application-consumer-test.yml`

```yaml
app:
  csc:
    service-url: http://localhost:19000
    oauth2:
      client-id: test-client
    credential-id: test-credential
    hash-algorithm-oid: 2.16.840.1.101.3.4.2.1
    digest-algorithm: SHA-256
    pin: test-pin-1234   # ← added
```

### 3. Add `pin` to `application-cdc-test.yml`

```yaml
app:
  csc:
    service-url: http://localhost:19000
    oauth2:
      client-id: test-client
    credential-id: test-credential
    hash-algorithm-oid: 2.16.840.1.101.3.4.2.1
    digest-algorithm: SHA-256
    pin: test-pin-1234   # ← added
```

## Verification

After the changes, run:

```bash
# Confirm no stale env var references remain
grep -r "POSTGRES_HOST\|KAFKA_BOOTSTRAP_SERVERS" src/test/resources/

# Confirm pin is present in all three test configs
grep "pin:" src/test/resources/application-*.yml
```

Expected output for second command:
```
application-test.yml:    pin: test-pin-1234
application-consumer-test.yml:    pin: test-pin-1234
application-cdc-test.yml:    pin: test-pin-1234
```

## Scope

- 3 files changed, all in `src/test/resources/`
- No Java code changes
- No new tests required
- No shared infrastructure changes (`test-containers-start.sh` untouched)