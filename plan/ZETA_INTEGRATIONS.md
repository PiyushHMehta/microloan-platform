# Zeta Platform Integrations — Reference Guide

How this project uses Zeta-specific infrastructure components: **Atropos**, **Ganymede**, **Cipher (Sandbox)**, **Heracles**, and **Olympus**.

---

## Table of Contents

1. [Atropos (Event Streaming)](#1-atropos-event-streaming)
2. [Ganymede (Managed Database)](#2-ganymede-managed-database)
3. [Cipher / Sandbox (Authorization)](#3-cipher--sandbox-authorization)
4. [Heracles (API Gateway Routing)](#4-heracles-api-gateway-routing)
5. [Olympus (Structured Logging)](#5-olympus-structured-logging)
6. [Spring Boot Commons](#6-spring-boot-commons)
7. [Deployment Config Summary](#7-deployment-config-summary)

---

## 1. Atropos (Event Streaming)

Atropos is Zeta's event streaming/pub-sub platform backed by AWS Kinesis. Used for publishing domain events and subscribing via webhooks.

### 1.1 Maven Dependency

```xml
<dependency>
    <groupId>in.zeta.oms</groupId>
    <artifactId>atropos-client</artifactId>
    <version>2.2.13</version>
    <exclusions>
        <exclusion>
            <groupId>org.hibernate</groupId>
            <artifactId>hibernate-core-jakarta</artifactId>
        </exclusion>
        <exclusion>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-core</artifactId>
        </exclusion>
        <exclusion>
            <groupId>javax.xml.bind</groupId>
            <artifactId>jaxb-api</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 1.2 Component Scan

In the main application class, scan the Atropos client package:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "in.zeta.springframework.boot.commons",
    "in.zeta.zea_2026_b01_charithap_microloan",
    "tech.zeta.microloan.cipher",
    "in.zeta.oms.atropos.client"   // <-- required for Atropos
})
public class MicroLoanApplication { ... }
```

### 1.3 Configuration Bean

Create a config class to instantiate the Atropos publisher client:

```java
package in.zeta.<your_app>.config;

import in.zeta.oms.atropos.client.AtroposPublisherClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AtroposPublisherConfig {

    @Bean
    public AtroposPublisherClient atroposPublisherClient() {
        return new AtroposPublisherClient();
    }
}
```

### 1.4 application.properties — Atropos Settings

```properties
# Publish mode: KINESIS for production
atropos.publish.mode=KINESIS

# Atropos infrastructure URLs (staging)
publisher.service.base.url=https://publisher-service-appinfra.internal.mum1-pp.zetaapps.in/
atropos.subscription.url=https://atropos-controller-appinfra.internal.mum1-pp.zetaapps.in/api/v1/unauth/tenants/0/registerWebhookSubscription

# Default topic
atropos.default.topic=microloan-charitha

# Per-event topic names
atropos.borrowerRegistered.topic=borrower-registered
atropos.kycStatusUpdated.topic=kyc-status-updated
atropos.loanIssued.topic=loan-issued
atropos.loanRejected.topic=loan-rejected
atropos.loanOverdue.topic=loan-overdue
atropos.loanClosed.topic=loan-closed
atropos.repaymentMade.topic=repayment-made
```

### 1.5 EventProducer — Low-Level Publishing

This class wraps `AtroposPublisherClient` and builds a `PubSubEvent`:

```java
package in.zeta.<your_app>.producer;

import com.google.gson.Gson;
import in.zeta.oms.atropos.client.AtroposPublisherClient;
import in.zeta.oms.atropos.model.PublishMode;
import in.zeta.oms.atropos.response.PublishEventResponse;
import olympus.pubsub.model.OperationType;
import olympus.pubsub.model.PubSubEvent;
import olympus.pubsub.model.TopicScope;
import org.apache.http.NameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Component
public class EventProducer {
    private final PublishMode publishMode;
    private final AtroposPublisherClient atroposPublisherClient;
    private final Gson gson;

    public EventProducer(
            AtroposPublisherClient atroposPublisherClient,
            Gson gson,
            @Value("${atropos.publish.mode}") String publishModeString
    ) {
        this.atroposPublisherClient = atroposPublisherClient;
        this.gson = gson;
        this.publishMode = PublishMode.valueOf(publishModeString.toUpperCase());
    }

    public CompletionStage<PublishEventResponse> publishEvent(
            String objectId,
            Map<String, Object> data,
            String topic,
            TopicScope topicScope
    ) {
        PubSubEvent.Builder builder = new PubSubEvent.Builder()
                .tenant("0")
                .topicScope(topicScope)              // TopicScope.SYSTEM
                .objectType(topic)                    // topic name as object type
                .objectID(objectId)
                .operationType(OperationType.CREATED)
                .sourceAttributes(new NameValuePair[0])
                .tags(List.of())
                .stateMachineState("default")
                .data(gson.toJsonTree(data));         // payload as JsonElement

        return atroposPublisherClient.publish(builder, publishMode);
    }
}
```

### 1.6 EventPublisher — High-Level Facade

Injects topic names from properties and wraps each event type:

```java
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final EventProducer eventProducer;

    @Value("${atropos.borrowerRegistered.topic}")
    private String borrowerTopic;

    // ... one @Value per topic ...

    public void publishBorrowerRegistered(Map<String, Object> payload) {
        publish("BorrowerRegistered", payload, borrowerTopic);
    }

    private void publish(String eventType, Map<String, Object> payload, String topic) {
        String objectId = payload.getOrDefault("loanId",
                payload.getOrDefault("borrowerId", "id")).toString();

        // Build envelope with eventId, eventType, occurredAt, payload
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", objectId);
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", Instant.now().atOffset(ZoneOffset.UTC).toString());
        envelope.put("payload", payload);

        eventProducer.publishEvent(objectId, envelope, topic, TopicScope.SYSTEM);
    }
}
```

### 1.7 Deployment — Atropos Topics & Subscriptions

In the cluster spec's `zone-values/<app>-module.yaml`, define topics and webhook subscriptions:

**Topics** (under `resourceRequirements.god.atroposTopics`):

```yaml
atroposTopics:
  borrower-registered-topic:
    syncPriority: "-20"
    name: _system_0_borrower-registered     # naming: _system_0_<topic-name>
    retentionPeriodInDays: 1
  kyc-status-updated-topic:
    syncPriority: "-20"
    name: _system_0_kyc-status-updated
    retentionPeriodInDays: 1
  # ... one entry per topic
```

**Subscriptions** (under `resourceRequirements.god.atroposSubscriptions`):

```yaml
atroposSubscriptions:
  borrower-registered-notification-subscription:
    syncPriority: "-10"                       # must be > topic's priority (runs after topic)
    subscriptionType: webhook
    subscriptionID: subscription_0_borrower_registered_notification_<your_name>
    subscriber: <your-app-name>
    topic: _system_0_borrower-registered      # must match topic name
    transformerJS: |-
      function transform(payload, sender) {
          return JSON.stringify(payload.data);
      }
    webhookRequest: |-
      {
        "method": "POST",
        "url": "https://<your-app>-{{ .Release.Namespace.Stripped }}.internal.cce.zetaapps.in/api/notifications/webhook",
        "postData": {
          "mimeType": "application/json",
          "text": "<% print(generateRequestBody(payload.data)); %>"
        }
      }
```

**Helm templates** — just include the common templates:

```yaml
# templates/atropos-topic.yaml
{{- template "common-resource-requirement.atropos-topic" . }}

# templates/atropos-subscription.yaml
{{- template "common-resource-requirement.atropos-subscription" . }}
```

**App-level env** (zone-values `<app>-app.yaml`):

```yaml
envProperties:
  atropos.publish.mode: 'KINESIS'
```

### 1.8 Key Imports

```java
import in.zeta.oms.atropos.client.AtroposPublisherClient;
import in.zeta.oms.atropos.model.PublishMode;
import in.zeta.oms.atropos.response.PublishEventResponse;
import in.zeta.oms.atropos.response.PublishStatus;
import olympus.pubsub.model.OperationType;
import olympus.pubsub.model.PubSubEvent;
import olympus.pubsub.model.TopicScope;
```

---

## 2. Ganymede (Managed Database)

Ganymede is Zeta's managed relational database service. It provisions PostgreSQL instances and manages credentials via Vault.

### 2.1 Deployment — Ganymede Relational Store

In `zone-values/<app>-module.yaml`:

```yaml
resourceRequirements:
  god:
    ganymedeRelationalStore:
      ganymede-microloan-db:
        appName: <your-app-name>
        dbCode: loandb                # unique DB code
        dbEngine: postgres

    ddl:
      ganymede-microloan-ddl:
        dbCode: loandb                # must match above
        dbEngine: postgres
        script: ddl-dev-snapshot/<your-app>-ddl/V1.0.2/V1.0.2-SNAPSHOT.tar.gz
```

**Helm template**:

```yaml
# templates/ganymede-relational-store.yaml
{{- template "common-resource-requirement.ganymedeRelationalStore" . }}

# templates/ddl.yaml
{{- template "common-resource-requirement.ddl" . }}
```

### 2.2 Datasource Config via Vault

In `zone-values/<app>-app.yaml`, credentials are fetched from Vault automatically:

```yaml
envProperties:
  spring.datasource.url: vault:secrets/data/cluster/{{ .Release.Namespace }}/<your-app>/tenants/0/ganymede/<dbCode>/master#jdbc.url
  spring.datasource.username: vault:secrets/data/cluster/{{ .Release.Namespace }}/<your-app>/tenants/0/ganymede/<dbCode>/master#username
  spring.datasource.password: vault:secrets/data/cluster/{{ .Release.Namespace }}/<your-app>/tenants/0/ganymede/<dbCode>/master#password
```

**Pattern**: `vault:secrets/data/cluster/<namespace>/<app>/tenants/0/ganymede/<dbCode>/master#<field>`

### 2.3 App-Level Overrides (CCE Deployment)

In production (`-app.yaml`), Flyway is disabled and JPA ddl-auto is `none`:

```yaml
envProperties:
  SPRING_FLYWAY_ENABLED: false
  SPRING_JPA_HIBERNATE_DDL_AUTO: none
```

DDL migrations are managed via the Ganymede DDL resource (tar.gz scripts applied during deployment).

### 2.4 Local Development

For local development (`application-local.properties`), use a local PostgreSQL directly:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/microloan
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=postgres
spring.datasource.password=<your-local-password>

spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

---

## 3. Cipher / Sandbox (Authorization)

Cipher is Zeta's authorization framework. It uses the `@SandboxAuthorizedSync` annotation to protect API endpoints with action-based access control. Each resource type needs an `ObjectProvider` implementation.

### 3.1 Key Concepts

| Concept | Description |
|---|---|
| `@SandboxAuthorizedSync` | Annotation on controller methods; intercepts requests for authz |
| `action` | `"<resource>.<operation>"` — what the caller wants to do |
| `object` | `"$$<resource-path>$$@<OBJECT_TYPE>.cipher.app"` — the object being accessed |
| `tenantID` | Hardcoded tenant identifier (e.g. `"1001034"`) |
| `ObjectProvider` | Returns an object payload for Cipher to evaluate policies against |

### 3.2 Component Scan

Include the Cipher package in your `@ComponentScan`:

```java
@ComponentScan(basePackages = {
    "in.zeta.springframework.boot.commons",    // includes sandbox auth
    "tech.zeta.microloan.cipher",              // your cipher providers
    "in.zeta.<your_app>",
})
```

### 3.3 CipherPayload DTO

A simple object returned by all providers:

```java
package in.zeta.<your_app>.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CipherPayload {
    private String objectType;
}
```

### 3.4 ObjectProvider Implementation (one per resource)

Each resource type needs a provider that implements `ObjectProvider<CipherPayload>`:

```java
package in.zeta.<your_app>.provider;

import in.zeta.oms.sandbox.model.object.ObjectProvider;
import in.zeta.oms.sandbox.model.realm.Realm;
import olympus.common.JID;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class BorrowerProvider implements ObjectProvider<CipherPayload> {

    public static final String OBJECT_TYPE = "Borrower";   // used in @SandboxAuthorizedSync

    @Override
    public CompletionStage<Optional<CipherPayload>> getObject(JID jid, Realm realm, Long tenantID) {
        CipherPayload payload = new CipherPayload(OBJECT_TYPE);
        return CompletableFuture.completedFuture(Optional.of(payload));
    }
}
```

### 3.5 Providers in This Project

| Provider Class | OBJECT_TYPE | Protects |
|---|---|---|
| `BorrowerProvider` | `"Borrower"` | `/api/borrowers/**` |
| `BorrowerKYCProvider` | `"BorrowerKYC"` | `/api/kyc/**` |
| `LoanProvider` | `"Loan"` | `/api/loans/**` |
| `LoanApplicationProvider` | `"LoanApplication"` | `/api/loan-applications/**` |
| `LoanProductProvider` | `"LoanProduct"` | `/api/loan-products/**` |
| `RepaymentProvider` | `"Repayment"` | `/api/repayments/**` |
| `RepaymentScheduleProvider` | `"RepaymentSchedule"` | `/api/repayment-schedules/**` |
| `LedgerProvider` | `"Ledger"` | `/api/ledger/**` |

### 3.6 Controller Usage Pattern

```java
@PostMapping
@SandboxAuthorizedSync(
    action = "loanProduct.create",                                          // <resource>.<operation>
    object = "$$loan-products$$@" + LoanProductProvider.OBJECT_TYPE + ".cipher.app",  // $$<path>$$@<TYPE>.cipher.app
    tenantID = "1001034"
)
public ResponseEntity<LoanProductResponse> createLoanProduct(...) { ... }
```

**Pattern breakdown**:
- `action`: `"<resourceName>.<operationName>"` — e.g. `borrower.getById`, `loan.cancel`, `kyc.submit`
- `object`: `"$$<api-path-segment>$$@<ProviderObjectType>.cipher.app"`
- `tenantID`: Always `"1001034"` (hardcoded for sandbox)

### 3.7 Key Imports

```java
import in.zeta.springframework.boot.commons.authorization.sandboxAccessControl.SandboxAuthorizedSync;
import in.zeta.oms.sandbox.model.object.ObjectProvider;
import in.zeta.oms.sandbox.model.realm.Realm;
import olympus.common.JID;
```

### 3.8 Proteus / Cipher Endpoints (application.properties)

```properties
proteus.endpoint=https://api.stage.zeta.in/zeta.in
certstore.proteus.endpoint=https://sb1-god-cipher.mum1-pp.zetaapps.in/proteus/zeta.in
sessions.proteus.endpoint=https://sb1-god-cipher.mum1-pp.zetaapps.in/proteus/zeta.in
```

---

## 4. Heracles (API Gateway Routing)

Heracles is Zeta's API gateway (built on Kong). It routes external traffic to your service inside the cluster.

### 4.1 Helm Template

Create `templates/heracles.yaml` in the cluster spec:

```yaml
apiVersion: zeta.tech/v1alpha1
kind: HeraclesClusterRoute
metadata:
  name: <your-route-name>
  namespace: {{ .Release.Namespace }}
spec:
  ingressType:
    - internal
    - public                          # or just internal for private APIs
  cluster: {{ .Release.Namespace }}
  domainClass: cluster-ingress
  paths:
    - /<your-app-name>/(.*)           # regex capture group for path rewrite
  backend:
    port: 8080
    service: <your-app-name>
  config:
    upstream-setting:
      proxy-setting:
        protocol: http
        path: /
        connect_timeout: 60000        # ms
        retries: 3
        read_timeout: 60000
        write_timeout: 60000
      route-setting:
        methods: "POST,PUT,DELETE,PATCH,GET,OPTIONS"
        preserve_host: false
        strip_path: false
    request-transformer:
      my-transformer:
        replace:
          uri: /$(uri_captures[1])    # strips the app prefix, forwards rest of path
```

### 4.2 How It Works

- External request to `https://<domain>/<your-app-name>/api/borrowers`
- Heracles matches `/<your-app-name>/(.*)`
- Captures `api/borrowers` and rewrites URI to `/api/borrowers`
- Forwards to your service on port 8080

---

## 5. Olympus (Structured Logging)

Olympus provides structured, traceable logging via `SpectraLogger`.

### 5.1 Logger Setup

```java
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;

public class MyService {
    private static final SpectraLogger logger = OlympusSpectra.getLogger(MyService.class);
}
```

### 5.2 Usage — Structured Attributes

```java
// Info with attributes
logger.info("Processing loan application")
    .attr("borrowerId", borrowerId)
    .attr("amount", amount)
    .log();                              // MUST call .log() to emit

// Error with exception
logger.error("Payment failed: " + e.getMessage(), e)
    .attr("loanId", loanId)
    .log();

// Warning
logger.warn("Invalid publish mode: " + mode).log();
```

### 5.3 Log4j2 Configuration (log4Olympus2.xml)

Place `log4Olympus2.xml` in the project root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration monitorInterval="5" status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%highlight{%d{HH:mm:ss} %-5p %-30.30c{1} %-10.10t %-10.10X{flowID} - %m%n}" />
        </Console>
    </Appenders>
    <Loggers>
        <!-- App logs (Spectra/Olympus) -->
        <Logger name="tech.zeta" level="TRACE" additivity="false">
            <AppenderRef ref="Console" />
        </Logger>
        <!-- Suppress metrics noise -->
        <Logger name="olympus.metrics" level="ERROR" additivity="false">
            <AppenderRef ref="Console" />
        </Logger>
        <Root level="INFO">
            <AppenderRef ref="Console" />
        </Root>
    </Loggers>
</Configuration>
```

### 5.4 Deployment Logging Config

In `helm-chart/values.yaml`:

```yaml
envProperties:
  LOGGING_PIPELINE: KINESIS_AND_SOUT         # logs to both Kinesis and stdout
  KINESIS_ACCESSKEY: vault:secrets/data/zone/common/logging#KINESIS_ACCESSKEY
  KINESIS_SECRETKEY: vault:secrets/data/zone/common/logging#KINESIS_SECRETKEY
  KINESIS_REGION: vault:secrets/data/zone/common/logging#KINESIS_REGION
```

### 5.5 Key Imports

```java
import in.zeta.spectra.capture.SpectraLogger;
import olympus.trace.OlympusSpectra;
import olympus.common.JID;
```

---

## 6. Spring Boot Commons

Zeta's `spring-boot-commons` library provides Cipher/Sandbox integration, Proteus auth, and common utilities.

### 6.1 Maven Dependency

```xml
<parent>
    <groupId>in.zeta</groupId>
    <artifactId>zeta-spring-boot-pom</artifactId>
    <version>4.1.20</version>
    <relativePath />
</parent>

<dependency>
    <groupId>in.zeta</groupId>
    <artifactId>spring-boot-commons</artifactId>
    <version>5.2.17</version>
    <exclusions>
        <!-- Many exclusions needed — see pom.xml for full list -->
        <exclusion>
            <groupId>com.vaadin.external.google</groupId>
            <artifactId>android-json</artifactId>
        </exclusion>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
        <!-- ... see project pom.xml for all exclusions -->
    </exclusions>
</dependency>
```

### 6.2 What It Provides

- `@SandboxAuthorizedSync` annotation (Cipher integration)
- `ObjectProvider` interface (`in.zeta.oms.sandbox.model.object.ObjectProvider`)
- Realm model (`in.zeta.oms.sandbox.model.realm.Realm`)
- Proteus authentication framework integration
- Common JID utilities (`olympus.common.JID`)

### 6.3 Required Component Scan

```java
@ComponentScan(basePackages = {
    "in.zeta.springframework.boot.commons"     // REQUIRED — enables Cipher, Proteus, etc.
})
```

### 6.4 Embedded Server

The project uses **Jetty** (not Tomcat). Tomcat starters are excluded, and Jetty is included:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
    <version>3.5.11</version>
</dependency>
```

---

## 7. Deployment Config Summary

### 7.1 Cluster Spec Structure

```
cluster_spec.<app>-cce-deploy/
├── ci.Jenkinsfile
├── ci.yaml
├── spec.yaml
├── CODEOWNERS
├── cdd-templates/
│   └── cce/
│       ├── descriptor.yaml                 # lists zone-value files
│       └── zone-values/
│           ├── <app>-app.yaml              # app-level config (env vars, datasource, Atropos mode)
│           └── <app>-module.yaml           # infra resources (Atropos topics, Ganymede DB, DDL)
├── helm-chart/
│   ├── Chart.yaml                          # chart deps: common, reloader, cleaner, common-resource-requirement
│   ├── values.yaml                         # image, replicas, env, health checks
│   └── templates/
│       ├── atropos-topic.yaml              # {{- template "common-resource-requirement.atropos-topic" . }}
│       ├── atropos-subscription.yaml       # {{- template "common-resource-requirement.atropos-subscription" . }}
│       ├── ddl.yaml                        # {{- template "common-resource-requirement.ddl" . }}
│       ├── ganymede-relational-store.yaml   # {{- template "common-resource-requirement.ganymedeRelationalStore" . }}
│       └── heracles.yaml                   # HeraclesClusterRoute CRD
└── release-notes/
    └── <version>/notes.md
```

### 7.2 Chart Dependencies

```yaml
dependencies:
  - name: common
    version: 1.0.98
    repository: '@chartrepo'
  - name: reloader
    repository: '@chartrepo'
    version: v0.1.2
  - name: cleaner
    version: 1.0.4
    repository: '@chartrepo'
  - name: <your-app>
    repository: '@chartrepo'
    version: <your-version>
  - name: common-resource-requirement
    version: 3.1.50
    repository: '@chartrepo'
```

### 7.3 Health Check

```yaml
service:
  port: 8080
  targetPort: 8080
  healthCheckPath: /health
  livenessProbeInitialDelaySeconds: 90
  readinessProbeInitialDelaySeconds: 120
```

---

## Quick Checklist — Adding Zeta Integrations to a New Project

- [ ] **Parent POM**: `in.zeta:zeta-spring-boot-pom:4.1.20`
- [ ] **Dependencies**: `spring-boot-commons` (5.2.17), `atropos-client` (2.2.13)
- [ ] **Embedded Server**: Use Jetty, exclude Tomcat
- [ ] **Component Scan**: `in.zeta.springframework.boot.commons`, `in.zeta.oms.atropos.client`
- [ ] **Logging**: Use `OlympusSpectra.getLogger()` + `SpectraLogger`, add `log4Olympus2.xml`
- [ ] **Authorization**: Create `ObjectProvider` per resource, annotate controllers with `@SandboxAuthorizedSync`
- [ ] **Events**: Create `AtroposPublisherConfig`, `EventProducer`, `EventPublisher`; configure topics in properties
- [ ] **Database**: Configure Ganymede relational store in module YAML; use Vault paths for credentials
- [ ] **Gateway**: Add `HeraclesClusterRoute` in Helm templates
- [ ] **Helm**: Include `common-resource-requirement` chart; add template files for topics, subscriptions, DDL, Ganymede
