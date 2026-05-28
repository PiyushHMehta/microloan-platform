# Microloan Platform — Complete Development Plan

A rule-driven microloan system where products define constraints, loans generate schedules, repayments follow FIFO, and lifecycle is managed via KFS and installment states.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Tech Stack](#2-tech-stack)
3. [Environment Configuration](#3-environment-configuration)
4. [Entity Definitions & DDL](#4-entity-definitions--ddl)
5. [Enums](#5-enums)
6. [Business Rules](#6-business-rules)
7. [State Machines](#7-state-machines)
8. [Constraints & Validations](#8-constraints--validations)
9. [API Endpoints & Service Logic](#9-api-endpoints--service-logic)
10. [Exception Handling & Error Codes](#10-exception-handling--error-codes)
11. [Event System](#11-event-system)
12. [Repository Custom Queries](#12-repository-custom-queries)
13. [Transaction Boundaries](#13-transaction-boundaries)
14. [Development Phases](#14-development-phases)
15. [Project Structure](#15-project-structure)
16. [Appendices](#16-appendices)

---

## 1. Architecture Overview

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│  REST APIs  │────▶│  Service     │────▶│  Repository  │
│ (Controller)│     │  Layer       │     │  (JPA)       │
└─────────────┘     └──────┬───────┘     └──────┬───────┘
                           │                     │
                    ┌──────▼───────┐      ┌──────▼───────┐
                    │  Event       │      │  PostgreSQL  │
                    │  Publisher   │      │              │
                    └──────────────┘      └──────────────┘
```

### End-to-End Flow

```
Borrower registers → KYC initiate (submit doc) → OTP verify → KYC level upgrade → Discovers loan products
→ Applies for loan → Admin approves → Loan created (KFS_PENDING)
→ Borrower accepts KFS → Loan ACTIVE, installments generated, money disbursed
→ Borrower makes repayments (FIFO) → Installments cleared
→ Scheduler detects overdue → Penalties applied
→ All installments paid → Loan CLOSED
```

### Logical Modules

| Module | Responsibility |
|---|---|
| `borrower-service` | Borrower CRUD + profile management |
| `kyc-service` | KYC document submission + OTP verification + level upgrade |
| `loan-product-service` | Loan product + frequency CRUD (admin) |
| `loan-application-service` | Application submission + approval/rejection |
| `loan-service` | Loan creation, KFS generation, KFS acceptance, status management |
| `installment-service` | Installment read operations |
| `repayment-service` | Payment processing, FIFO allocation, prepayment |
| `overdue-scheduler` | Cron-based overdue detection + penalty application |
| `event-service` | Spring Application Events (internal pub/sub) |

---

## 2. Tech Stack

| Component | Choice |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 15+ |
| Migration | Flyway |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Mapping | MapStruct (compile-time, type-safe — preferred over ModelMapper) |
| Validation | Jakarta Bean Validation (`@Valid`) |
| Scheduling | `@Scheduled` (Spring) |
| Events | Spring ApplicationEventPublisher |
| Testing | JUnit 5 + Mockito + Testcontainers |
| Build | Maven / Gradle |

---

## 3. Environment Configuration

### 3.1 Local (`application-local.yml`)

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/microloan_db
    username: microloan_user
    password: microloan_pass
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration

app:
  loan:
    max-emi-to-income-ratio: 0.50
    interest-model: FLAT
    default-penalty-rate: 2.0
  scheduler:
    overdue-cron: "0 0 1 * * *"
    enabled: true
  pagination:
    default-page-size: 20
    max-page-size: 100

logging:
  level:
    root: INFO
    com.microloan: DEBUG
    org.hibernate.SQL: DEBUG
```

### 3.2 Dev / Staging (`application-dev.yml`)

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:dev-db.internal}:${DB_PORT:5432}/${DB_NAME:microloan_db}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true

app:
  loan:
    max-emi-to-income-ratio: 0.50
    interest-model: FLAT
  scheduler:
    overdue-cron: "0 0 1 * * *"
    enabled: true
  pagination:
    default-page-size: 20
    max-page-size: 100

logging:
  level:
    root: INFO
    com.microloan: INFO
```

### 3.3 Required Environment Variables (Dev/Prod)

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `dev-db.internal` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `microloan_db` |
| `DB_USERNAME` | Database user | `microloan_svc` |
| `DB_PASSWORD` | Database password | (secret) |
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` / `local` |

---

## 4. Entity Definitions & DDL

### 4.1 Borrower

```sql
CREATE TABLE borrower (
    borrower_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name                VARCHAR(150)   NOT NULL,
    phone_number             VARCHAR(20)    NOT NULL UNIQUE,
    email                    VARCHAR(150),
    date_of_birth            DATE,
    gender                   VARCHAR(10),
    monthly_income           DECIMAL(12,2)  NOT NULL,
    annual_household_income  DECIMAL(12,2)  NOT NULL,
    kyc_level                VARCHAR(20)    NOT NULL DEFAULT 'NO_KYC',
    address_line1            VARCHAR(255),
    address_line2            VARCHAR(255),
    city                     VARCHAR(100),
    state                    VARCHAR(100),
    pincode                  VARCHAR(20),
    is_active                BOOLEAN        DEFAULT TRUE,
    created_at               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_borrower_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_borrower_kyc CHECK (kyc_level IN ('NO_KYC', 'MIN_KYC', 'FULL_KYC')),
    CONSTRAINT chk_borrower_income CHECK (monthly_income > 0),
    CONSTRAINT chk_borrower_annual_income CHECK (annual_household_income > 0)
);

CREATE INDEX idx_borrower_phone ON borrower(phone_number);
CREATE INDEX idx_borrower_kyc ON borrower(kyc_level);
CREATE INDEX idx_borrower_city ON borrower(city);
CREATE INDEX idx_borrower_active ON borrower(is_active);
```

**JPA Notes:** Use `@PreUpdate` to auto-set `updated_at`. Map `kyc_level` and `gender` as Java enums with `@Enumerated(EnumType.STRING)`.

### 4.2 KYC

```sql
CREATE TABLE kyc (
    kyc_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id      UUID UNIQUE NOT NULL,
    pan_number       VARCHAR(10) UNIQUE,
    aadhaar_number   VARCHAR(12) UNIQUE,
    pan_verified     BOOLEAN DEFAULT FALSE,
    aadhaar_verified BOOLEAN DEFAULT FALSE,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_kyc_borrower FOREIGN KEY (borrower_id)
        REFERENCES borrower(borrower_id),
    CONSTRAINT chk_pan_format CHECK (
        pan_number IS NULL OR pan_number ~ '^[A-Z]{5}[0-9]{4}[A-Z]$'
    ),
    CONSTRAINT chk_aadhaar_format CHECK (
        aadhaar_number IS NULL OR aadhaar_number ~ '^\d{12}$'
    )
);
```

**KYC → Level Derivation:**

| PAN Verified | Aadhaar Verified | KYC Level |
|---|---|---|
| false | false | NO_KYC |
| true | false | MIN_KYC |
| false | true | MIN_KYC |
| true | true | FULL_KYC |

### 4.2.1 OTP Verification

```sql
CREATE TABLE otp_verification (
    otp_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id     UUID           NOT NULL,
    document_type   VARCHAR(10)    NOT NULL,
    otp_code        VARCHAR(6)     NOT NULL,
    expires_at      TIMESTAMP      NOT NULL,
    verified        BOOLEAN        DEFAULT FALSE,
    attempts        INT            DEFAULT 0,
    created_at      TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_otp_borrower FOREIGN KEY (borrower_id)
        REFERENCES borrower(borrower_id),
    CONSTRAINT chk_otp_doc_type CHECK (document_type IN ('PAN', 'AADHAAR')),
    CONSTRAINT chk_otp_attempts CHECK (attempts >= 0 AND attempts <= 5)
);

CREATE INDEX idx_otp_borrower ON otp_verification(borrower_id);
CREATE INDEX idx_otp_expires ON otp_verification(expires_at);
```

**OTP Rules:**
- 6-digit random numeric code
- Expires in 10 minutes
- Max 5 verification attempts per OTP
- Only latest unverified OTP per (borrower_id, document_type) is valid
- On successful verification: mark OTP verified, update KYC record, derive KYC level

### 4.3 LoanProduct

```sql
CREATE TABLE loan_product (
    product_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(100)  NOT NULL,
    description       TEXT,
    min_principal     DECIMAL(12,2) NOT NULL,
    max_principal     DECIMAL(12,2) NOT NULL,
    min_tenure_months  INT           NOT NULL,  -- in months
    max_tenure_months  INT           NOT NULL,  -- in months
    interest_rate     DECIMAL(5,2)  NOT NULL,
    penalty_rate      DECIMAL(5,2)  DEFAULT 0,
    min_kyc_level     VARCHAR(20)   NOT NULL,
    is_active         BOOLEAN       DEFAULT TRUE,
    created_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_product_principal CHECK (min_principal > 0 AND max_principal >= min_principal),
    CONSTRAINT chk_product_tenure CHECK (min_tenure_months > 0 AND max_tenure_months >= min_tenure_months),
    CONSTRAINT chk_product_interest CHECK (interest_rate > 0 AND interest_rate <= 100),
    CONSTRAINT chk_product_penalty CHECK (penalty_rate >= 0),
    CONSTRAINT chk_product_kyc CHECK (min_kyc_level IN ('NO_KYC', 'MIN_KYC', 'FULL_KYC'))
);
```

### 4.4 LoanProductFrequency

```sql
CREATE TABLE loan_product_frequency (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID        NOT NULL,
    frequency    VARCHAR(20) NOT NULL,

    CONSTRAINT fk_freq_product FOREIGN KEY (product_id)
        REFERENCES loan_product(product_id) ON DELETE CASCADE,
    CONSTRAINT uq_product_frequency UNIQUE (product_id, frequency),
    CONSTRAINT chk_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY'))
);
```

### 4.5 LoanApplication

```sql
CREATE TABLE loan_application (
    application_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id          UUID           NOT NULL,
    product_id           UUID           NOT NULL,
    requested_amount     DECIMAL(12,2)  NOT NULL,
    requested_tenure_months INT         NOT NULL,  -- always in months
    repayment_frequency  VARCHAR(20)    NOT NULL,
    purpose              TEXT,
    status               VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    rejection_reason     TEXT,
    reviewed_by          VARCHAR(100),
    created_at           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_app_borrower FOREIGN KEY (borrower_id)
        REFERENCES borrower(borrower_id),
    CONSTRAINT fk_app_product FOREIGN KEY (product_id)
        REFERENCES loan_product(product_id),
    CONSTRAINT chk_app_amount CHECK (requested_amount > 0),
    CONSTRAINT chk_app_tenure CHECK (requested_tenure_months > 0),
    CONSTRAINT chk_app_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_app_frequency CHECK (repayment_frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_app_borrower ON loan_application(borrower_id);
CREATE INDEX idx_app_status ON loan_application(status);
```

### 4.6 Loan

```sql
CREATE TABLE loan (
    loan_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id             UUID           NOT NULL,
    application_id          UUID           UNIQUE,
    product_id              UUID           NOT NULL,
    principal_amount        DECIMAL(12,2)  NOT NULL,
    interest_rate           DECIMAL(5,2)   NOT NULL,
    tenure_months            INT            NOT NULL,  -- always in months
    total_amount            DECIMAL(12,2)  NOT NULL,
    total_penalty_amount    DECIMAL(12,2)  DEFAULT 0,
    total_payable           DECIMAL(12,2)  NOT NULL,  -- total_amount + total_penalty_amount
    total_paid              DECIMAL(12,2)  DEFAULT 0,
    epi_amount              DECIMAL(12,2)  NOT NULL,
    repayment_frequency     VARCHAR(20)    NOT NULL,
    penalty_rate            DECIMAL(5,2)   DEFAULT 0,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'KFS_PENDING',
    kfs_generated_at        TIMESTAMP,
    kfs_acknowledged_at     TIMESTAMP,
    kfs_snapshot            JSONB,
    disbursed_at            TIMESTAMP,
    created_at              TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_loan_borrower FOREIGN KEY (borrower_id)
        REFERENCES borrower(borrower_id),
    CONSTRAINT fk_loan_application FOREIGN KEY (application_id)
        REFERENCES loan_application(application_id),
    CONSTRAINT fk_loan_product FOREIGN KEY (product_id)
        REFERENCES loan_product(product_id),
    CONSTRAINT chk_loan_principal CHECK (principal_amount > 0),
    CONSTRAINT chk_loan_total CHECK (total_amount >= principal_amount),
    CONSTRAINT chk_loan_penalty CHECK (total_penalty_amount >= 0),
    CONSTRAINT chk_loan_payable CHECK (total_payable >= total_amount),
    CONSTRAINT chk_loan_paid CHECK (total_paid >= 0 AND total_paid <= total_payable),
    CONSTRAINT chk_loan_status CHECK (status IN ('KFS_PENDING', 'ACTIVE', 'OVERDUE', 'CLOSED')),
    CONSTRAINT chk_loan_frequency CHECK (repayment_frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY'))
);

CREATE INDEX idx_loan_borrower ON loan(borrower_id);
CREATE INDEX idx_loan_status ON loan(status);
```

### 4.7 Installment

```sql
CREATE TABLE installment (
    installment_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id          UUID           NOT NULL,
    installment_no   INT            NOT NULL,
    due_date         DATE           NOT NULL,
    epi_amount       DECIMAL(12,2)  NOT NULL,
    penalty_amount   DECIMAL(12,2)  DEFAULT 0,
    total_due        DECIMAL(12,2)  NOT NULL,
    amount_paid      DECIMAL(12,2)  DEFAULT 0,
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    penalty_applied  BOOLEAN        DEFAULT FALSE,
    paid_at          TIMESTAMP,
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_inst_loan FOREIGN KEY (loan_id)
        REFERENCES loan(loan_id),
    CONSTRAINT chk_inst_status CHECK (status IN ('PENDING', 'PARTIAL', 'PAID', 'OVERDUE')),
    CONSTRAINT chk_inst_amounts CHECK (amount_paid >= 0 AND amount_paid <= total_due),
    CONSTRAINT uq_loan_installment UNIQUE (loan_id, installment_no)
);

CREATE INDEX idx_inst_loan ON installment(loan_id);
CREATE INDEX idx_inst_status ON installment(status);
CREATE INDEX idx_inst_due_date ON installment(due_date);
```

### 4.8 Repayment

```sql
CREATE TABLE repayment (
    repayment_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id           UUID           NOT NULL,
    amount            DECIMAL(12,2)  NOT NULL,
    payment_reference VARCHAR(100)   UNIQUE NOT NULL,
    payment_mode      VARCHAR(20),
    payment_status    VARCHAR(20)    DEFAULT 'SUCCESS',
    paid_at           TIMESTAMP      NOT NULL,
    created_at        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rep_loan FOREIGN KEY (loan_id)
        REFERENCES loan(loan_id),
    CONSTRAINT chk_rep_amount CHECK (amount > 0),
    CONSTRAINT chk_rep_mode CHECK (payment_mode IN ('CASH', 'UPI', 'BANK_TRANSFER')),
    CONSTRAINT chk_rep_status CHECK (payment_status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_rep_loan ON repayment(loan_id);
```

**Design Decision — No `borrower_id` in Repayment:** Derivable via `repayment.loan_id → loan.borrower_id`. No denormalization needed.

---

## 5. Enums

```java
public enum KycLevel {
    NO_KYC(0), MIN_KYC(1), FULL_KYC(2);
    private final int rank;
    KycLevel(int rank) { this.rank = rank; }
    public boolean meetsRequirement(KycLevel required) {
        return this.rank >= required.rank;
    }
}

public enum Gender { MALE, FEMALE, OTHER }

public enum RepaymentFrequency { WEEKLY, BIWEEKLY, MONTHLY }

public enum ApplicationStatus { PENDING, APPROVED, REJECTED }

public enum LoanStatus { KFS_PENDING, ACTIVE, OVERDUE, CLOSED }

public enum InstallmentStatus { PENDING, PARTIAL, PAID, OVERDUE }

public enum PaymentMode { CASH, UPI, BANK_TRANSFER }

public enum PaymentStatus { SUCCESS, FAILED }
```

---

## 6. Business Rules

### 6.1 Interest Calculation (Flat)

```
total_interest = principal × (annual_interest_rate / 100) × (tenure_months / 12)
total_amount   = principal + total_interest
```

### 6.2 EPI Calculation

EPI = Equated Periodic Installment.

```
epi_amount = total_amount / number_of_installments
```

Number of installments depends on frequency:

| Frequency | Installments |
|---|---|
| MONTHLY | `tenure_months` |
| BIWEEKLY | `tenure_months × 2` |
| WEEKLY | `tenure_months × 4` |

Round EPI to 2 decimal places (HALF_UP). Adjust last installment for rounding difference:
```
lastInstEpi = totalAmount - (epiAmount × (numInstallments - 1))
```

### 6.3 Installment Schedule Generation

Starting from today + 1 period:
- **MONTHLY**: `dueDate.plusMonths(1)` per installment
- **BIWEEKLY**: `dueDate.plusDays(14)` per installment
- **WEEKLY**: `dueDate.plusDays(7)` per installment

### 6.4 EPI Affordability Check (RBI Rule)

Monthly repayment burden must not exceed 50% of borrower's monthly income:

```
MONTHLY:  epiAmount <= monthlyIncome × 0.50
BIWEEKLY: epiAmount × 2 <= monthlyIncome × 0.50   (normalize to monthly)
WEEKLY:   epiAmount × 4 <= monthlyIncome × 0.50   (normalize to monthly)
```

### 6.5 FIFO Repayment Logic

```
1. Fetch all unpaid installments ORDER BY installment_no ASC
2. Lock rows (SELECT ... FOR UPDATE)
3. For each installment (oldest first):
     remaining_for_inst = total_due - amount_paid
     allocation = min(remaining_payment, remaining_for_inst)
     installment.amount_paid += allocation
     remaining_payment -= allocation
     if amount_paid == total_due → status = PAID, paid_at = now
     else if amount_paid > 0    → status = PARTIAL
4. Update loan.total_paid += original payment amount
5. Recalculate loan status
6. Commit transaction
```

### 6.6 Penalty Calculation

```
penalty_amount = epi_amount × (penalty_rate / 100)
```

- Applied **once** per overdue installment (flag: `penalty_applied = true`)
- After penalty: `total_due = epi_amount + penalty_amount`
- Penalty increases what borrower owes; FIFO allocation includes penalty in `total_due`

### 6.7 Prepayment

- Borrower pays remaining balance in one shot
- Remaining balance = `SUM(total_due - amount_paid)` across all unpaid installments (includes any penalties)
- All remaining installments marked PAID
- Loan `total_paid` updated, `total_payable` recalculated
- Loan → CLOSED
- **No prepayment penalty**

### 6.8 KFS (Key Fact Statement)

Generated on loan creation. Stored as `kfs_snapshot JSONB` on loan for audit trail. Contains:
- Principal, interest rate, total interest, total amount, EPI amount
- Full installment schedule (installment_no, due_date, amount)
- Penalty terms, repayment frequency
- Installment rows are only materialized in DB **after** borrower accepts KFS

### 6.9 Loan Status Derivation

| Condition | Loan Status |
|---|---|
| KFS not yet accepted | `KFS_PENDING` |
| KFS accepted, no overdue installments | `ACTIVE` |
| Any installment is OVERDUE | `OVERDUE` |
| All installments PAID | `CLOSED` |

Recalculated after every repayment and every overdue scheduler run.

### 6.10 Overdue Recovery

When repayment clears an overdue installment:
- Installment: OVERDUE → PAID
- If no overdue installments remain: Loan OVERDUE → ACTIVE
- Triggered automatically by `recalculateStatus()`

---

## 7. State Machines

### 7.1 Application Lifecycle

```
PENDING ──approve──▶ APPROVED ──(auto)──▶ Loan created (KFS_PENDING)
   │
   └──reject──▶ REJECTED (terminal)
```

- Cannot approve/reject a non-PENDING application
- Cannot re-approve or re-reject

### 7.2 Loan Lifecycle

```
KFS_PENDING ──accept KFS──▶ ACTIVE ◀──▶ OVERDUE
                               │
                               └── all paid ──▶ CLOSED (terminal)
``` 

### 7.3 Installment Lifecycle

```
PENDING ──partial payment──▶ PARTIAL ──full payment──▶ PAID
   │                            │
   └──due date passed──▶ OVERDUE ──payment──▶ PAID
```

---

## 8. Constraints & Validations

### 8.1 Field-Level (Bean Validation on DTOs)

| Entity | Field | Validation |
|---|---|---|
| Borrower | `full_name` | `@NotBlank`, `@Size(max=150)` |
| Borrower | `phone_number` | `@NotBlank`, `@Pattern(regexp="^[6-9]\\d{9}$")` |
| Borrower | `email` | `@Email` (optional) |
| Borrower | `monthly_income` | `@Positive`, `@DecimalMin("1000")` |
| Borrower | `date_of_birth` | `@Past` |
| KYC | `pan_number` | `@Pattern(regexp="^[A-Z]{5}[0-9]{4}[A-Z]$")` |
| KYC | `aadhaar_number` | `@Pattern(regexp="^\\d{12}$")` |
| LoanProduct | `interest_rate` | `@Positive`, `@DecimalMax("100")` |
| LoanProduct | `min_principal` | `@Positive` |
| LoanApplication | `requested_amount` | `@Positive` |
| Repayment | `amount` | `@Positive` |
| Repayment | `payment_reference` | `@NotBlank` |

### 8.2 Business-Level (Service Layer)

| Rule | Service | Enforcement |
|---|---|---|
| Phone unique | BorrowerService | DB unique + catch `DataIntegrityViolationException` |
| PAN unique | KycService | Same |
| Aadhaar unique | KycService | Same |
| One KYC per borrower | KycService | `borrower_id UNIQUE` on kyc table |
| Principal within product range | LoanApplicationService | `min_principal <= amount <= max_principal` |
| Tenure within product range | LoanApplicationService | `min_tenure_months <= tenure_months <= max_tenure_months` |
| Frequency allowed for product | LoanApplicationService | Check `loan_product_frequency` table |
| KYC eligibility | LoanApplicationService | `borrower.kyc_level >= product.min_kyc_level` |
| EPI affordability | LoanApplicationService | Monthly burden ≤ 50% of monthly income |
| RBI income limit | LoanApplicationService | `annual_household_income <= 300000` |
| Loan already exists for app | LoanService | `application_id UNIQUE` on loan table |
| Cannot pay closed/undisbursed loan | RepaymentService | Check `loan.status` ∈ {ACTIVE, OVERDUE} |
| Cannot overpay | RepaymentService | `amount <= SUM(total_due - amount_paid)` from installments |
| Payment ref unique (idempotency) | RepaymentService | DB unique constraint |

---

## 9. API Endpoints & Service Logic

### 9.0 Pagination (all list endpoints)

Query params: `?page=0&size=20&sort=created_at,desc`

Response wrapper:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

### 9.1 Endpoints Summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/borrowers` | Public | Register borrower |
| `PUT` | `/api/v1/borrowers/{id}` | Borrower | Update profile |
| `GET` | `/api/v1/borrowers/{id}` | Borrower/Admin | Get borrower |
| `GET` | `/api/v1/borrowers` | Admin | List borrowers (filtered) |
| `POST` | `/api/v1/kyc/initiate` | Borrower | Submit KYC document + generate OTP |
| `POST` | `/api/v1/kyc/verify-otp` | Borrower | Verify OTP → complete KYC verification |
| `GET` | `/api/v1/kyc/{borrowerId}` | Borrower/Admin | Get KYC details |
| `POST` | `/api/v1/loan-products` | Admin | Create product |
| `PUT` | `/api/v1/loan-products/{id}` | Admin | Update product |
| `DELETE` | `/api/v1/loan-products/{id}` | Admin | Soft-delete (deactivate) |
| `GET` | `/api/v1/loan-products/{id}` | Public | Get product |
| `GET` | `/api/v1/loan-products` | Public | List products (filtered) |
| `POST` | `/api/v1/loan-applications` | Borrower | Apply for loan |
| `GET` | `/api/v1/loan-applications/{id}` | Borrower/Admin | Get application |
| `GET` | `/api/v1/loan-applications` | Admin | List applications (filtered) |
| `POST` | `/api/v1/loan-applications/{id}/approve` | Admin | Approve application |
| `POST` | `/api/v1/loan-applications/{id}/reject` | Admin | Reject application |
| `GET` | `/api/v1/loans/{id}` | Borrower/Admin | Get loan details |
| `GET` | `/api/v1/loans` | Admin | List loans (filtered) |
| `GET` | `/api/v1/loans/{id}/kfs` | Borrower | Get KFS |
| `POST` | `/api/v1/loans/{id}/kfs/accept` | Borrower | Accept KFS → disburse |
| `GET` | `/api/v1/loans/{id}/installments` | Borrower/Admin | List installments |
| `POST` | `/api/v1/repayments` | Borrower | Make payment |
| `GET` | `/api/v1/loans/{id}/repayments` | Borrower/Admin | List repayments for loan |

---

### 9.2 BorrowerService

#### POST /api/v1/borrowers

**Method:** `BorrowerService.create(BorrowerCreateRequest dto)`
**Transactional:** Yes
**Idempotent:** No (phone_number UNIQUE prevents true duplicates)

```
Input:
{
  full_name, phone_number, email, date_of_birth, gender,
  monthly_income, annual_household_income,
  address_line1, address_line2, city, state, pincode
}
```

**Logic:**

```
1. Bean validation (@Valid on controller)
   - full_name: @NotBlank, @Size(max=150)
   - phone_number: @NotBlank, @Pattern("^[6-9]\\d{9}$")
   - email: @Email (nullable)
   - date_of_birth: @Past (nullable)
   - gender: valid enum MALE/FEMALE/OTHER (nullable)
   - monthly_income: @Positive, @DecimalMin("1000")
   - annual_household_income: @Positive

2. Map DTO → Borrower entity
   - Set kyc_level = NO_KYC
   - Set is_active = true
   - Set created_at = now(), updated_at = now()

3. borrowerRepository.save(borrower)
   - CATCH DataIntegrityViolationException (phone_number unique)
     → throw DuplicateResourceException(BRW_002, "Phone number already registered")

4. Publish event: BorrowerRegisteredEvent { borrower_id, phone_number, timestamp }

5. Map entity → BorrowerResponse
   Return: { borrower_id, full_name, phone_number, kyc_level, is_active, created_at }
```

**Errors:** BRW_001 (phone format), BRW_002 (phone duplicate), BRW_004 (income invalid)

---

#### PUT /api/v1/borrowers/{borrower_id}

**Method:** `BorrowerService.update(UUID borrowerId, BorrowerUpdateRequest dto)`
**Transactional:** Yes
**Idempotent:** Yes

```
Input (all fields nullable — partial update):
{
  full_name?, email?, date_of_birth?, gender?,
  monthly_income?, annual_household_income?,
  address_line1?, address_line2?, city?, state?, pincode?
}
```

**Logic:**

```
1. Borrower borrower = borrowerRepository.findById(borrowerId)
   - NOT FOUND → throw ResourceNotFoundException(BRW_003)

2. Check: borrower.is_active == true
   - FALSE → throw BusinessException(BRW_005, "Borrower is deactivated")

3. Apply non-null fields from DTO to entity (patch-style):
   if (dto.getFullName() != null) borrower.setFullName(dto.getFullName());
   if (dto.getEmail() != null) borrower.setEmail(dto.getEmail());
   ... etc for each nullable field

4. NOTE: phone_number NOT updatable (immutable identifier)
   NOTE: kyc_level NOT updatable via this endpoint (managed by KYC service)

5. borrower.setUpdatedAt(now())   // or @PreUpdate handles this

6. borrowerRepository.save(borrower)

7. Map entity → BorrowerResponse, return
```

**Errors:** BRW_003 (not found), BRW_005 (deactivated)

---

#### GET /api/v1/borrowers/{borrower_id}

**Method:** `BorrowerService.getById(UUID borrowerId)`
**Transactional:** Read-only

```
1. Borrower borrower = borrowerRepository.findById(borrowerId)
   - NOT FOUND → throw ResourceNotFoundException(BRW_003)

2. Map entity → BorrowerResponse (full profile), return
```

---

#### GET /api/v1/borrowers

**Method:** `BorrowerService.list(BorrowerFilterRequest filters, Pageable pageable)`
**Transactional:** Read-only

```
Input (query params, all optional):
  ?city=Bangalore&state=Karnataka&kyc_level=MIN_KYC&is_active=true
  &page=0&size=20&sort=created_at,desc

Logic:
1. Build dynamic query using Specification<Borrower>:
   - if city != null       → WHERE city = :city
   - if state != null      → AND state = :state
   - if kycLevel != null   → AND kyc_level = :kycLevel
   - if isActive != null   → AND is_active = :isActive

2. Page<Borrower> result = borrowerRepository.findAll(spec, pageable)

3. Map Page<Borrower> → PagedResponse<BorrowerResponse>, return
```

---

### 9.3 KycService

#### POST /api/v1/kyc/initiate

**Method:** `KycService.initiateKyc(KycInitiateRequest dto)`
**Transactional:** Yes
**Auth:** Borrower

```
Input:
{
  "borrower_id": "uuid",
  "document_type": "PAN",
  "document_value": "ABCDE1234F"
}
```

**Logic:**

```
1. ── VALIDATE BORROWER ──
   Borrower borrower = borrowerRepository.findById(dto.borrowerId)
   - NOT FOUND → throw ResourceNotFoundException(BRW_003)
   - borrower.isActive == false → throw BusinessException(BRW_005)

2. ── VALIDATE DOCUMENT FORMAT ──
   CASE "PAN":
     Validate: "^[A-Z]{5}[0-9]{4}[A-Z]$" → else KYC_001
   CASE "AADHAAR":
     Validate: "^\\d{12}$" → else KYC_002

3. ── CHECK DOCUMENT UNIQUENESS ──
   CASE "PAN":
     existing = kycRepository.findByPanNumber(dto.documentValue)
     IF found AND borrower_id != dto.borrowerId → throw DuplicateResourceException(KYC_003)
   CASE "AADHAAR":
     existing = kycRepository.findByAadhaarNumber(dto.documentValue)
     IF found AND borrower_id != dto.borrowerId → throw DuplicateResourceException(KYC_004)

4. ── UPSERT KYC RECORD (store doc, do NOT mark verified yet) ──
   kyc = kycRepository.findByBorrowerId(dto.borrowerId) || new Kyc(borrowerId)
   CASE "PAN":  kyc.setPanNumber(dto.documentValue)
   CASE "AADHAAR": kyc.setAadhaarNumber(dto.documentValue)
   kycRepository.save(kyc)

5. ── GENERATE OTP ──
   otpCode = random 6-digit numeric string (SecureRandom)
   OtpVerification otp = new OtpVerification(
     borrowerId, documentType, otpCode,
     expiresAt = now() + 10 minutes,
     verified = false, attempts = 0
   )
   otpVerificationRepository.save(otp)

6. ── PUBLISH EVENT (OTP logged — simulates SMS) ──
   Publish: OtpGeneratedEvent { borrower_id, document_type, otp_code }

7. Return: { message: "OTP sent for verification", document_type, expires_in_seconds: 600 }
```

**Errors:** BRW_003, BRW_005, KYC_001, KYC_002, KYC_003, KYC_004

---

#### POST /api/v1/kyc/verify-otp

**Method:** `KycService.verifyOtp(KycVerifyOtpRequest dto)`
**Transactional:** Yes (updates OTP + KYC + Borrower)
**Auth:** Borrower

```
Input:
{
  "borrower_id": "uuid",
  "document_type": "PAN",
  "otp": "123456"
}
```

**Logic:**

```
1. ── FETCH LATEST OTP ──
   OtpVerification otp = otpVerificationRepository
     .findLatestUnverified(dto.borrowerId, dto.documentType)
   - NOT FOUND → throw BusinessException(KYC_008, "No pending OTP found")

2. ── CHECK EXPIRY ──
   otp.expiresAt < now() → throw BusinessException(KYC_008, "OTP has expired")

3. ── CHECK ATTEMPT LIMIT ──
   otp.attempts >= 5 → throw BusinessException(KYC_010, "Max OTP attempts exceeded")

4. ── INCREMENT ATTEMPTS ──
   otp.setAttempts(otp.getAttempts() + 1)
   otpVerificationRepository.save(otp)

5. ── VERIFY OTP ──
   IF dto.otp != otp.otpCode:
     throw BusinessException(KYC_009, "Invalid OTP")
     // attempts already incremented above

6. ── MARK OTP VERIFIED ──
   otp.setVerified(true)
   otpVerificationRepository.save(otp)

7. ── UPDATE KYC RECORD ──
   Kyc kyc = kycRepository.findByBorrowerId(dto.borrowerId)
     → else KYC_006

   CASE "PAN":   kyc.setPanVerified(true)
   CASE "AADHAAR": kyc.setAadhaarVerified(true)
   kycRepository.save(kyc)

8. ── DERIVE KYC LEVEL ──
   both verified   → FULL_KYC
   either verified → MIN_KYC
   neither         → NO_KYC

9. ── UPDATE BORROWER KYC LEVEL (if changed) ──
   Borrower borrower = borrowerRepository.findById(dto.borrowerId)
   IF newLevel != borrower.kycLevel:
     oldLevel = borrower.kycLevel
     borrower.setKycLevel(newLevel)
     borrowerRepository.save(borrower)
     Publish: KycLevelUpgradedEvent { borrower_id, oldLevel, newLevel }

10. Publish: KycUpdatedEvent { borrower_id, document_type }
    Publish: OtpVerifiedEvent { borrower_id, document_type }

11. Map kyc → KycResponse, return
    { kyc_id, borrower_id, pan_number, aadhaar_number,
      pan_verified, aadhaar_verified, kyc_level, created_at }
```

**Errors:** KYC_006, KYC_008 (expired/not found), KYC_009 (invalid OTP), KYC_010 (max attempts)

---

#### GET /api/v1/kyc/{borrower_id}

**Method:** `KycService.getByBorrowerId(UUID borrowerId)`
**Transactional:** Read-only

```
1. Borrower borrower = borrowerRepository.findById(borrowerId)
   - NOT FOUND → throw ResourceNotFoundException(BRW_003)

2. Kyc kyc = kycRepository.findByBorrowerId(borrowerId)
   - NOT FOUND → throw ResourceNotFoundException(KYC_006)

3. Map → KycResponse (include borrower.kyc_level), return
```

---

### 9.4 LoanProductService

#### POST /api/v1/loan-products

**Method:** `LoanProductService.create(LoanProductCreateRequest dto)`
**Transactional:** Yes
**Auth:** Admin only

```
Input:
{
  "name": "Personal Loan Basic",
  "description": "Short term loan",
  "min_principal": 10000, "max_principal": 500000,
  "min_tenure_months": 6, "max_tenure_months": 36,
  "interest_rate": 12.5, "penalty_rate": 2.0,
  "min_kyc_level": "MIN_KYC",
  "frequencies": ["MONTHLY", "BIWEEKLY"]
}
```

**Logic:**

```
1. Bean validation (@Valid):
   - name: @NotBlank
   - min/max_principal: @Positive
   - min/max_tenure_months: @Positive
   - interest_rate: @Positive, @DecimalMax("100")
   - penalty_rate: @PositiveOrZero
   - frequencies: @NotEmpty

2. Business validation:
   a. min_principal <= max_principal → else PRD_002
   b. min_tenure_months <= max_tenure_months → else PRD_002
   c. frequencies not empty → else PRD_004

3. Map DTO → LoanProduct entity
   - Set is_active = true, created_at/updated_at = now()

4. loanProductRepository.save(product)

5. For each frequency in dto.frequencies:
   Save LoanProductFrequency row

6. Map → LoanProductResponse (include frequencies[]), return
```

---

#### PUT /api/v1/loan-products/{product_id}

**Method:** `LoanProductService.update(UUID productId, LoanProductUpdateRequest dto)`
**Transactional:** Yes | **Auth:** Admin

```
Input (all nullable — partial update):
{
  "name"?, "description"?, "min_principal"?, "max_principal"?,
  "min_tenure_months"?, "max_tenure_months"?, "interest_rate"?, "penalty_rate"?,
  "min_kyc_level"?, "frequencies"?
}

Logic:
1. Product = findById(productId) → else PRD_001
2. Apply non-null fields (patch-style)
3. Re-validate ranges (min <= max for principal and tenure_months)
4. IF dto.frequencies != null:
   - Delete existing frequencies for product
   - Save new frequency rows
5. Save product, return LoanProductResponse
```

---

#### DELETE /api/v1/loan-products/{product_id}

**Method:** `LoanProductService.deactivate(UUID productId)`
**Transactional:** Yes | **Auth:** Admin
**Soft delete** — sets `is_active = false`. Does NOT physically delete.

```
1. Product = findById(productId) → else PRD_001
2. product.setIsActive(false)
3. Save, return 204 No Content
```

---

#### GET /api/v1/loan-products/{product_id}

**Method:** `LoanProductService.getById(UUID productId)`
**Transactional:** Read-only

```
1. Product = findById(productId) → else PRD_001
2. Fetch frequencies for product
3. Map → LoanProductResponse { ...fields, frequencies[] }, return
```

---

#### GET /api/v1/loan-products

**Method:** `LoanProductService.list(LoanProductFilterRequest filters, Pageable pageable)`
**Transactional:** Read-only

```
Input: ?is_active=true&min_kyc_level=MIN_KYC&page=0&size=20

Logic:
1. Build Specification<LoanProduct> from filters
2. Fetch page, eagerly load frequencies (@EntityGraph or JOIN FETCH)
3. Map → PagedResponse<LoanProductResponse>, return
```

---

### 9.5 LoanApplicationService

#### POST /api/v1/loan-applications

**Method:** `LoanApplicationService.apply(LoanApplicationCreateRequest dto)`
**Transactional:** Yes | **Auth:** Borrower

```
Input:
{
  "borrower_id": "uuid",
  "product_id": "uuid",
  "requested_amount": 200000,
  "requested_tenure_months": 12,
  "repayment_frequency": "MONTHLY",
  "purpose": "Education loan"
}
```

**Logic:**

```
1. ── FETCH ENTITIES ──
   Borrower borrower = borrowerRepository.findById(dto.borrowerId)
     → else BRW_003
   LoanProduct product = loanProductRepository.findById(dto.productId)
     → else PRD_001

2. ── BORROWER CHECK ──
   borrower.isActive == true → else APP_008 ("Borrower is not active")

3. ── RBI INCOME ELIGIBILITY ──
   borrower.annualHouseholdIncome <= 300000
     → else APP_010 ("Annual household income ₹%s exceeds RBI microfinance limit of ₹3,00,000")

4. ── PRODUCT CHECK ──
   product.isActive == true → else APP_009 ("Loan product is not active")

5. ── KYC ELIGIBILITY ──
   borrower.kycLevel.meetsRequirement(product.minKycLevel)
     → else APP_004 ("KYC level insufficient: required %s, current %s")

6. ── PRINCIPAL RANGE ──
   product.minPrincipal <= dto.requestedAmount <= product.maxPrincipal
     → else APP_001 ("Amount %s outside range [%s, %s]")

7. ── TENURE RANGE ──
   product.minTenureMonths <= dto.requestedTenureMonths <= product.maxTenureMonths
     → else APP_002 ("Tenure %d months outside range [%d, %d]")

8. ── FREQUENCY CHECK ──
   allowedFreqs = loanProductFrequencyRepository.findFrequenciesByProductId(productId)
   allowedFreqs.contains(dto.repaymentFrequency)
     → else APP_003 ("Frequency %s not allowed for this product")

9. ── EPI AFFORDABILITY (RBI rule) ──
   a. totalInterest = requestedAmount × (interestRate / 100) × (tenureMonths / 12.0)
   b. totalAmount = requestedAmount + totalInterest
   c. numInstallments = computeInstallmentCount(tenureMonths, frequency)
        MONTHLY → tenureMonths | BIWEEKLY → tenureMonths × 2 | WEEKLY → tenureMonths × 4
   d. epiAmount = totalAmount / numInstallments (round 2dp HALF_UP)
   e. Normalize to monthly:
        MONTHLY  → monthlyBurden = epiAmount
        BIWEEKLY → monthlyBurden = epiAmount × 2
        WEEKLY   → monthlyBurden = epiAmount × 4
   f. maxAllowed = borrower.monthlyIncome × 0.50
   g. monthlyBurden <= maxAllowed
     → else APP_005 ("Monthly burden ₹%s exceeds 50%% of income ₹%s")

10. ── CREATE APPLICATION ──
   LoanApplication app = new LoanApplication(
     borrowerId, productId, requestedAmount, requestedTenureMonths,
     repaymentFrequency, purpose, status=PENDING
   )
   loanApplicationRepository.save(app)

11. Publish: LoanApplicationCreatedEvent { application_id, borrower_id, product_id, amount }

12. Map → LoanApplicationResponse, return
```

**Errors:** BRW_003, PRD_001, APP_001–APP_005, APP_008, APP_009, APP_010

---

#### POST /api/v1/loan-applications/{application_id}/approve

**Method:** `LoanApplicationService.approve(UUID applicationId, ApproveRequest dto)`
**Transactional:** Yes | **Auth:** Admin

```
Input: { "reviewed_by": "admin123" }

Logic:
1. LoanApplication app = findById(applicationId) → else APP_006
2. app.status == PENDING → else APP_007 ("Not in PENDING status")
3. No loan already exists: loanRepository.findByApplicationId(id) → if found, LOAN_003
4. app.setStatus(APPROVED), app.setReviewedBy(dto.reviewedBy)
   loanApplicationRepository.save(app)
5. Publish: LoanApplicationApprovedEvent { application_id, reviewed_by }
6. ── TRIGGER LOAN CREATION ──
   LoanResponse loan = loanService.createFromApplication(app)
7. Map → LoanApplicationResponse (include loan_id), return
```

---

#### POST /api/v1/loan-applications/{application_id}/reject

**Method:** `LoanApplicationService.reject(UUID applicationId, RejectRequest dto)`
**Transactional:** Yes | **Auth:** Admin

```
Input: { "reviewed_by": "admin123", "rejection_reason": "Low credit score" }

Logic:
1. LoanApplication app = findById(applicationId) → else APP_006
2. app.status == PENDING → else APP_007
3. app.setStatus(REJECTED), app.setReviewedBy(...), app.setRejectionReason(...)
   loanApplicationRepository.save(app)
4. Publish: LoanApplicationRejectedEvent { application_id, reviewed_by, reason }
5. Map → LoanApplicationResponse, return
```

---

#### GET /api/v1/loan-applications/{application_id}

**Method:** `LoanApplicationService.getById(UUID applicationId)`
**Transactional:** Read-only

```
1. App = findById(applicationId) → else APP_006
2. Map → LoanApplicationResponse (full details), return
```

---

#### GET /api/v1/loan-applications

**Method:** `LoanApplicationService.list(filters, pageable)`
**Transactional:** Read-only

```
Input: ?status=PENDING&borrower_id=uuid&product_id=uuid&page=0&size=20

Logic:
1. Build Specification<LoanApplication> from filters
2. Fetch page
3. Map → PagedResponse<LoanApplicationResponse>, return
```

---

### 9.6 LoanService

#### Internal: createFromApplication (called after approval — NOT a public endpoint)

**Method:** `LoanService.createFromApplication(LoanApplication application)`
**Transactional:** Participates in caller's transaction

```
Input: the approved LoanApplication entity

1. ── FETCH PRODUCT ──
   LoanProduct product = loanProductRepository.findById(application.productId)

2. ── COMPUTE LOAN TERMS ──
   principal      = application.requestedAmount
   interestRate   = product.interestRate          // copy from product
   penaltyRate    = product.penaltyRate           // copy from product
   tenureMonths   = application.requestedTenureMonths
   frequency      = application.repaymentFrequency

   totalInterest  = principal × (interestRate / 100) × (tenureMonths / 12.0)
   totalAmount    = principal + totalInterest
   numInstallments = computeInstallmentCount(tenureMonths, frequency)
   epiAmount      = totalAmount / numInstallments (round 2dp HALF_UP)
   lastInstEpi    = totalAmount - (epiAmount × (numInstallments - 1))

3. ── CREATE LOAN ──
   Loan loan = new Loan(
     borrowerId, applicationId, productId,
     principalAmount=principal, interestRate, penaltyRate,
     tenureMonths, totalAmount, totalPenaltyAmount=0,
     totalPayable=totalAmount, totalPaid=0, epiAmount,
     repaymentFrequency=frequency, status=KFS_PENDING
   )
   loanRepository.save(loan)

4. ── GENERATE KFS SNAPSHOT ──
   Build installment schedule PREVIEW (not saved to installment table yet):

   List<KfsInstallment> schedule = []
   LocalDate dueDate = LocalDate.now()

   for i in 1..numInstallments:
     dueDate = advanceDate(dueDate, frequency)
       MONTHLY  → dueDate.plusMonths(1)
       BIWEEKLY → dueDate.plusDays(14)
       WEEKLY   → dueDate.plusDays(7)

     amount = (i == numInstallments) ? lastInstEpi : epiAmount

     schedule.add({ installment_no: i, due_date: dueDate, epi_amount: amount })

   KFS kfsSnapshot = {
     loan_id, borrower_id, principal, interest_rate,
     total_interest, total_amount, epi_amount,
     repayment_frequency, tenure_months, num_installments,
     penalty_rate, repayment_schedule: schedule, generated_at: now()
   }

   loan.setKfsSnapshot(kfsSnapshot)     // stored as JSONB
   loan.setKfsGeneratedAt(now())
   loanRepository.save(loan)

5. Publish: LoanCreatedEvent { loan_id, borrower_id, principal }
   Publish: KfsGeneratedEvent { loan_id }

6. Map → LoanResponse, return
```

---

#### GET /api/v1/loans/{loan_id}/kfs

**Method:** `LoanService.getKfs(UUID loanId)`
**Transactional:** Read-only

```
1. Loan loan = findById(loanId) → else LOAN_001
2. Return loan.kfsSnapshot mapped to KfsResponse {
     loan_id, principal, interest_rate, total_amount,
     epi_amount, repayment_frequency, penalty_rate,
     repayment_schedule: [ { installment_no, due_date, amount } ... ],
     status: loan.status == KFS_PENDING ? "PENDING_ACCEPTANCE" : "ACCEPTED"
   }
```

---

#### POST /api/v1/loans/{loan_id}/kfs/accept

**Method:** `LoanService.acceptKfs(UUID loanId)`
**Transactional:** Yes | **Auth:** Borrower (owner)

```
1. Loan loan = findById(loanId) → else LOAN_001

2. loan.status == KFS_PENDING → else LOAN_002

3. ── UPDATE LOAN ──
   loan.setStatus(ACTIVE)
   loan.setKfsAcknowledgedAt(now())
   loan.setDisbursedAt(now())

4. ── GENERATE INSTALLMENT ROWS (actual DB rows) ──
   Read schedule from loan.kfsSnapshot.repayment_schedule

   for each entry in schedule:
     Installment inst = new Installment(
       loanId, installmentNo=entry.installment_no,
       dueDate=entry.due_date, epiAmount=entry.epi_amount,
       penaltyAmount=0, totalDue=entry.epi_amount,
       amountPaid=0, status=PENDING, penaltyApplied=false
     )
     installmentRepository.save(inst)

5. loanRepository.save(loan)

6. Publish: KfsAcceptedEvent { loan_id }
   Publish: LoanDisbursedEvent { loan_id, borrower_id, principal, disbursed_at }
   Publish: LoanStatusChangedEvent { loan_id, old: KFS_PENDING, new: ACTIVE }
   Publish: InstallmentsGeneratedEvent { loan_id, count }

7. Map → LoanResponse { loan_id, status: "ACTIVE", disbursed_at, ... }, return
```

**Errors:** LOAN_001 (not found), LOAN_002 (not KFS_PENDING)

---

#### GET /api/v1/loans/{loan_id}

**Method:** `LoanService.getById(UUID loanId)`
**Transactional:** Read-only

```
1. Loan loan = findById(loanId) → else LOAN_001
2. Map → LoanResponse {
     loan_id, borrower_id, application_id, product_id,
     principal_amount, interest_rate, tenure_months,
     total_amount, total_penalty_amount, total_payable,
     total_paid, remaining: total_payable - total_paid,
     epi_amount, repayment_frequency, penalty_rate,
     status, disbursed_at, created_at, updated_at
   }
```

---

#### GET /api/v1/loans

**Method:** `LoanService.list(filters, pageable)`
**Transactional:** Read-only

```
Input: ?status=ACTIVE&borrower_id=uuid&page=0&size=20

Logic:
1. Build Specification<Loan> from filters
2. Fetch page
3. Map → PagedResponse<LoanResponse>, return
```

---

#### Internal: recalculateStatus(UUID loanId)

**Note:** Called after repayment and overdue detection. Not a public endpoint.

```
1. Loan loan = findById(loanId)
2. List<Installment> installments = installmentRepository.findByLoanId(loanId)

3. Derive status:
   allPaid    = all installments have status PAID
   anyOverdue = any installment has status OVERDUE

   newStatus:
     if loan.status == KFS_PENDING → do NOT change
     else if allPaid               → CLOSED
     else if anyOverdue            → OVERDUE
     else                          → ACTIVE

4. Recalculate penalty totals from installments:
   totalPenalty = SUM(installment.penaltyAmount) for all installments
   loan.setTotalPenaltyAmount(totalPenalty)
   loan.setTotalPayable(loan.totalAmount + totalPenalty)

5. IF newStatus != loan.status:
     oldStatus = loan.status
     loan.setStatus(newStatus)
     Publish: LoanStatusChangedEvent { loan_id, old, new }
     IF newStatus == CLOSED:
       Publish: LoanClosedEvent { loan_id, borrower_id }

6. loanRepository.save(loan)
```

---

### 9.7 InstallmentService

#### GET /api/v1/loans/{loan_id}/installments

**Method:** `InstallmentService.getByLoanId(UUID loanId)`
**Transactional:** Read-only

```
1. Loan = findById(loanId) → else LOAN_001
2. List<Installment> = installmentRepository.findByLoanIdOrderByInstallmentNoAsc(loanId)
3. Map each → InstallmentResponse {
     installment_id, installment_no, due_date,
     epi_amount, penalty_amount, total_due,
     amount_paid, remaining: total_due - amount_paid,
     status, penalty_applied, paid_at
   }
4. Return list
```

---

### 9.8 RepaymentService

#### POST /api/v1/repayments

**Method:** `RepaymentService.processPayment(RepaymentRequest dto)`
**Transactional:** Yes — CRITICAL (single atomic transaction)
**Auth:** Borrower
**Concurrency:** Pessimistic locking (SELECT FOR UPDATE)

```
Input:
{
  "loan_id": "uuid",
  "amount": 25000,
  "payment_reference": "UPI123456789",
  "payment_mode": "UPI"
}
```

**Logic:**

```
1. ── VALIDATE INPUT ──
   a. dto.amount > 0 → else REP_001
   b. dto.paymentMode in (CASH, UPI, BANK_TRANSFER) → else REP_005

2. ── FETCH LOAN ──
   Loan loan = findById(dto.loanId) → else LOAN_001

3. ── LOAN STATUS CHECK ──
   loan.status == CLOSED → throw REP_002 ("Loan is closed")
   loan.status == KFS_PENDING → throw REP_002 ("Loan not yet disbursed")

4. ── OVERPAYMENT CHECK (source of truth: installment-level) ──
   remainingBalance = installmentRepository.sumRemainingByLoanId(dto.loanId)
     // SQL: SELECT SUM(total_due - amount_paid) FROM installment
     //       WHERE loan_id = :loanId AND status IN ('PENDING','PARTIAL','OVERDUE')
   dto.amount > remainingBalance → throw REP_003 ("Exceeds remaining ₹%s")

5. ── CREATE REPAYMENT RECORD ──
   Repayment repayment = new Repayment(
     loanId, amount, paymentReference, paymentMode,
     paymentStatus=SUCCESS, paidAt=now()
   )
   repaymentRepository.save(repayment)
   - CATCH DataIntegrityViolationException (payment_reference UNIQUE)
     → throw DuplicateResourceException(REP_004, "Duplicate payment reference")

6. ── FIFO ALLOCATION (CORE LOGIC) ──

   List<Installment> unpaidInstallments =
     installmentRepository.findUnpaidByLoanIdForUpdate(dto.loanId)

   SQL:
     SELECT * FROM installment
     WHERE loan_id = :loanId
       AND status IN ('PENDING', 'PARTIAL', 'OVERDUE')
     ORDER BY installment_no ASC
     FOR UPDATE

   BigDecimal remainingPayment = dto.amount
   List<UUID> fullyPaidInstallmentIds = []

   for (Installment inst : unpaidInstallments) {
     if (remainingPayment <= 0) break

     remainingForInst = inst.totalDue - inst.amountPaid
     allocation = min(remainingPayment, remainingForInst)

     inst.amountPaid += allocation
     remainingPayment -= allocation 

     if (inst.amountPaid == inst.totalDue) {
       inst.status = PAID
       inst.paidAt = now()
       fullyPaidInstallmentIds.add(inst.installmentId)
     } else if (inst.amountPaid > 0) {
       inst.status = PARTIAL
     }

     inst.updatedAt = now()
     installmentRepository.save(inst)
   }

7. ── UPDATE LOAN TOTALS ──
   loan.totalPaid += dto.amount
   // total_penalty_amount and total_payable recalculated by recalculateStatus()
   loanRepository.save(loan)

8. ── RECALCULATE LOAN STATUS ──
   loanService.recalculateStatus(dto.loanId)
   // Handles: ACTIVE / OVERDUE / CLOSED transitions

9. ── PUBLISH EVENTS ──
   Publish: RepaymentMadeEvent { repayment_id, loan_id, amount, payment_reference }
   for each fullyPaidInstallmentId:
     Publish: InstallmentPaidEvent { installment_id, loan_id, installment_no }
   // LoanStatusChanged / LoanClosed events published by recalculateStatus()

10. ── BUILD RESPONSE ──
    Reload loan for latest status:
    Return RepaymentResponse {
      repayment_id, loan_id, amount, payment_reference,
      payment_mode, payment_status: "SUCCESS",
      loan_status, remaining_balance, paid_at
    }
```

**Errors:** LOAN_001, REP_001–REP_005

**Edge Cases:**

| Scenario | Behavior |
|---|---|
| Exact single installment amount | One installment → PAID |
| Partial installment amount | One installment → PARTIAL |
| Amount covering multiple installments | Multiple installments → PAID in FIFO order |
| Full remaining balance (prepayment) | All installments → PAID, loan → CLOSED |
| Concurrent payments on same loan | FOR UPDATE lock serializes them |
| Duplicate payment_reference | Rejected at DB level (unique constraint) |
| Payment on overdue installment | Clears it (OVERDUE → PAID), loan may recover to ACTIVE |

---

#### GET /api/v1/loans/{loan_id}/repayments

**Method:** `RepaymentService.getByLoanId(UUID loanId)`
**Transactional:** Read-only

```
1. Loan = findById(loanId) → else LOAN_001
2. List<Repayment> = repaymentRepository.findByLoanIdOrderByPaidAtAsc(loanId)
3. Map each → RepaymentResponse { repayment_id, loan_id, amount,
     payment_reference, payment_mode, payment_status, paid_at, created_at }
4. Return list
```

---

### 9.9 OverdueDetectionService (Scheduler)

#### Scheduled: Daily Overdue Check

**Method:** `OverdueDetectionService.detectAndMarkOverdue()`
**Trigger:** `@Scheduled(cron = "${app.scheduler.overdue-cron}")` — daily at 1 AM
**Transactional:** Per-loan batch (each loan in its own transaction)

```
OUTER LOGIC (not transactional — orchestrator):

1. ── FIND ALL OVERDUE-CANDIDATE INSTALLMENTS ──

   Paginated to avoid loading entire table:
   int pageNum = 0
   do {
     Page<Installment> page = installmentRepository.findOverdueCandidates(
       LocalDate.now(), PageRequest.of(pageNum, 100)
     )

     SQL:
       SELECT * FROM installment
       WHERE due_date < CURRENT_DATE
         AND status IN ('PENDING', 'PARTIAL')
       ORDER BY loan_id, installment_no ASC

     Group by loan_id:
     Map<UUID, List<Installment>> byLoan = page.groupBy(i -> i.loanId)

     for each (loanId, installments) in byLoan:
       processOverdueForLoan(loanId, installments)   // separate TX

     pageNum++
   } while (page.hasNext())


INNER LOGIC — processOverdueForLoan(UUID loanId, List<Installment> overdueInstallments):
@Transactional (each loan isolated)

   1. Loan loan = loanRepository.findById(loanId)

   2. for each Installment inst in overdueInstallments:

      a. inst.setStatus(OVERDUE)

      b. IF inst.penaltyApplied == false:
           penaltyAmount = inst.epiAmount × (loan.penaltyRate / 100)
           inst.setPenaltyAmount(penaltyAmount)
           inst.setTotalDue(inst.epiAmount + penaltyAmount)
           inst.setPenaltyApplied(true)
           Publish: PenaltyAppliedEvent { installment_id, loan_id, penalty_amount }

      c. inst.setUpdatedAt(now())
         installmentRepository.save(inst)

      d. Publish: InstallmentOverdueEvent { installment_id, loan_id, due_date }

   3. ── UPDATE LOAN PENALTY TOTALS ──
      loanService.recalculateStatus(loanId)
      // recalculateStatus now also updates total_penalty_amount and total_payable
```

**Key Properties:**
- Each loan processed in its own transaction → one failure doesn't block others
- Penalty applied **once** (checked via `penalty_applied` flag)
- Idempotent — running twice on same day produces same result
- Penalty increases `total_due`, included in FIFO allocation

---

## 10. Exception Handling & Error Codes

### 10.1 Error Response Format

```json
{
  "timestamp": "2026-04-30T10:00:00Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "APP_001",
  "message": "Requested amount outside product range [10000, 500000]",
  "path": "/api/v1/loan-applications"
}
```

### 10.2 Global Exception Handler (`@RestControllerAdvice`)

| Exception | HTTP | Handling |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | Field validation errors → list of field errors |
| `BusinessException` | 400/409/422 | Business rule violation (code from ErrorCode enum) |
| `ResourceNotFoundException` | 404 | Entity not found |
| `InvalidStateException` | 409 | Invalid state transition |
| `DuplicateResourceException` | 409 | Unique constraint violation |
| `DataIntegrityViolationException` | 409 | DB-level duplicate key |
| `AccessDeniedException` | 403 | Role mismatch |
| `Exception` (fallback) | 500 | Log full trace, return safe message |

### 10.3 Error Code Catalog

#### Borrower (BRW_xxx)

| Code | HTTP | Message |
|---|---|---|
| `BRW_001` | 400 | Invalid phone number format |
| `BRW_002` | 409 | Phone number already registered |
| `BRW_003` | 404 | Borrower not found |
| `BRW_004` | 400 | Monthly income must be positive |
| `BRW_005` | 400 | Borrower is deactivated |

#### KYC (KYC_xxx)

| Code | HTTP | Message |
|---|---|---|
| `KYC_001` | 400 | Invalid PAN format (expected: ABCDE1234F) |
| `KYC_002` | 400 | Invalid Aadhaar format (expected: 12 digits, all numeric) |
| `KYC_003` | 409 | PAN already registered to another borrower |
| `KYC_004` | 409 | Aadhaar already registered to another borrower |
| `KYC_006` | 404 | KYC record not found for borrower |
| `KYC_008` | 400 | OTP expired or not found |
| `KYC_009` | 400 | Invalid OTP |
| `KYC_010` | 429 | Max OTP verification attempts exceeded |

#### Loan Product (PRD_xxx)

| Code | HTTP | Message |
|---|---|---|
| `PRD_001` | 404 | Loan product not found |
| `PRD_002` | 400 | min_principal must be ≤ max_principal |
| `PRD_003` | 400 | Product is inactive |
| `PRD_004` | 400 | At least one repayment frequency required |

#### Loan Application (APP_xxx)

| Code | HTTP | Message |
|---|---|---|
| `APP_001` | 400 | Requested amount outside product range |
| `APP_002` | 400 | Requested tenure outside product range |
| `APP_003` | 400 | Repayment frequency not allowed for this product |
| `APP_004` | 422 | Borrower KYC level insufficient |
| `APP_005` | 422 | EPI exceeds 50% of monthly income |
| `APP_006` | 404 | Loan application not found |
| `APP_007` | 409 | Application is not in PENDING status |
| `APP_008` | 400 | Borrower is not active |
| `APP_009` | 400 | Loan product is not active |
| `APP_010` | 422 | Annual household income exceeds RBI microfinance limit (₹3,00,000) |

#### Loan (LOAN_xxx)

| Code | HTTP | Message |
|---|---|---|
| `LOAN_001` | 404 | Loan not found |
| `LOAN_002` | 409 | Loan is not in KFS_PENDING status |
| `LOAN_003` | 409 | Loan already exists for this application |
| `LOAN_004` | 409 | Loan is already closed |
| `LOAN_005` | 409 | KFS already accepted |

#### Repayment (REP_xxx)

| Code | HTTP | Message |
|---|---|---|
| `REP_001` | 400 | Payment amount must be positive |
| `REP_002` | 409 | Loan is closed / not yet disbursed |
| `REP_003` | 400 | Payment amount exceeds remaining balance |
| `REP_004` | 409 | Duplicate payment reference |
| `REP_005` | 400 | Invalid payment mode |
| `REP_006` | 404 | Repayment not found |

#### Installment (INST_xxx)

(No standalone endpoints — installments accessed via loan)

---

## 11. Event System

### 11.1 Complete Event List

| Event | Trigger | Key Fields |
|---|---|---|
| `BorrowerRegisteredEvent` | POST /borrowers | borrower_id, phone_number |
| `KycUpdatedEvent` | POST /kyc/verify-otp | borrower_id, document_type |
| `KycLevelUpgradedEvent` | POST /kyc/verify-otp (if level changed) | borrower_id, old_level, new_level |
| `OtpGeneratedEvent` | POST /kyc/initiate | borrower_id, document_type, otp_code |
| `OtpVerifiedEvent` | POST /kyc/verify-otp | borrower_id, document_type |
| `LoanApplicationCreatedEvent` | POST /loan-applications | application_id, borrower_id, product_id, amount |
| `LoanApplicationApprovedEvent` | POST .../approve | application_id, reviewed_by |
| `LoanApplicationRejectedEvent` | POST .../reject | application_id, reviewed_by, reason |
| `LoanCreatedEvent` | Internal (after approval) | loan_id, borrower_id, principal |
| `KfsGeneratedEvent` | Internal (after loan creation) | loan_id |
| `KfsAcceptedEvent` | POST .../kfs/accept | loan_id |
| `LoanDisbursedEvent` | POST .../kfs/accept | loan_id, borrower_id, principal, disbursed_at |
| `InstallmentsGeneratedEvent` | POST .../kfs/accept | loan_id, count |
| `RepaymentMadeEvent` | POST /repayments | repayment_id, loan_id, amount, payment_reference |
| `InstallmentPaidEvent` | POST /repayments (per cleared inst) | installment_id, loan_id, installment_no |
| `InstallmentOverdueEvent` | Scheduler | installment_id, loan_id, installment_no, due_date |
| `PenaltyAppliedEvent` | Scheduler | installment_id, loan_id, penalty_amount |
| `LoanStatusChangedEvent` | recalculateStatus() | loan_id, old_status, new_status |
| `LoanClosedEvent` | recalculateStatus() (all paid) | loan_id, borrower_id |

### 11.2 Implementation

- Use `ApplicationEventPublisher` in service methods
- `@EventListener` in a dedicated listener class (initially: log all events)
- Foundation for future notification service (SMS/email)

---

## 12. Repository Custom Queries

```java
// BorrowerRepository
Optional<Borrower> findByPhoneNumber(String phoneNumber);

// KycRepository
Optional<Kyc> findByBorrowerId(UUID borrowerId);
Optional<Kyc> findByPanNumber(String panNumber);
Optional<Kyc> findByAadhaarNumber(String aadhaarNumber);

// OtpVerificationRepository
@Query("SELECT o FROM OtpVerification o WHERE o.borrowerId = :borrowerId " +
       "AND o.documentType = :documentType AND o.verified = false " +
       "ORDER BY o.createdAt DESC LIMIT 1")
Optional<OtpVerification> findLatestUnverified(
    @Param("borrowerId") UUID borrowerId,
    @Param("documentType") String documentType
);

// LoanProductFrequencyRepository
List<LoanProductFrequency> findByProductId(UUID productId);
void deleteByProductId(UUID productId);

// LoanRepository
Optional<Loan> findByApplicationId(UUID applicationId);
// + Specification-based filtered list

// InstallmentRepository
List<Installment> findByLoanIdOrderByInstallmentNoAsc(UUID loanId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Installment i WHERE i.loanId = :loanId " +
       "AND i.status IN ('PENDING', 'PARTIAL', 'OVERDUE') " +
       "ORDER BY i.installmentNo ASC")
List<Installment> findUnpaidByLoanIdForUpdate(@Param("loanId") UUID loanId);

@Query("SELECT COALESCE(SUM(i.totalDue - i.amountPaid), 0) FROM Installment i " +
       "WHERE i.loanId = :loanId AND i.status IN ('PENDING', 'PARTIAL', 'OVERDUE')")
BigDecimal sumRemainingByLoanId(@Param("loanId") UUID loanId);

@Query("SELECT i FROM Installment i WHERE i.dueDate < :today " +
       "AND i.status IN ('PENDING', 'PARTIAL')")
Page<Installment> findOverdueCandidates(@Param("today") LocalDate today, Pageable pageable);

// RepaymentRepository
List<Repayment> findByLoanIdOrderByPaidAtAsc(UUID loanId);
```

---

## 13. Transaction Boundaries

| Endpoint | Scope | Why |
|---|---|---|
| POST /borrowers | Single save | Simple create |
| POST /kyc/initiate | KYC upsert + OTP create | Document stored + OTP generated atomically |
| POST /kyc/verify-otp | OTP + KYC + Borrower update | OTP verification + KYC level upgrade must be atomic |
| POST /loan-products | Product + N frequencies | Parent + children atomic |
| POST /loan-applications | Single save | Simple create after validation |
| POST .../approve | Application + Loan + KFS | Approval + loan creation must be atomic |
| POST .../reject | Single save | Simple status update |
| POST .../kfs/accept | Loan + N installments | Loan activation + schedule generation atomic |
| POST /repayments | Repayment + N installments + Loan | FIFO allocation must be fully atomic |
| Overdue scheduler | Per-loan transaction | Isolate failures per loan |

---

## 14. Development Phases

### Phase 1: Project Setup (Day 1)

- [ ] Initialize Spring Boot project (Spring Initializr)
  - Dependencies: Web, JPA, PostgreSQL, Validation, Flyway, Lombok, DevTools
- [ ] Add SpringDoc OpenAPI + MapStruct dependencies
- [ ] Configure `application.yml`, `application-local.yml`
- [ ] Set up PostgreSQL locally (Docker Compose)
- [ ] Write Flyway migration scripts V1–V8
- [ ] Set up base package structure (see Section 15)
- [ ] Create `GlobalExceptionHandler`, `ErrorResponse`, `ErrorCode` enum
- [ ] Create `BusinessException`, `ResourceNotFoundException`, `InvalidStateException`, `DuplicateResourceException`
- [ ] Create `BaseEntity` (created_at, updated_at with `@PrePersist`/`@PreUpdate`)

### Phase 2: Borrower Module (Day 2)

- [ ] Entity: `Borrower` | Enums: `KycLevel`, `Gender`
- [ ] Repository: `BorrowerRepository` (with Specification support)
- [ ] DTOs: `BorrowerCreateRequest`, `BorrowerUpdateRequest`, `BorrowerResponse`
- [ ] Mapper: `BorrowerMapper` (MapStruct)
- [ ] Service: `BorrowerService` — create, update, getById, list
- [ ] Controller: `BorrowerController`
- [ ] Unit tests for service + integration test for controller

### Phase 3: KYC Module (Day 2-3)

- [ ] Entity: `Kyc`, `OtpVerification`
- [ ] DTOs: `KycInitiateRequest`, `KycVerifyOtpRequest`, `KycResponse`
- [ ] Mapper: `KycMapper`
- [ ] Service: `KycService` — initiateKyc (doc submission + OTP generation), verifyOtp (OTP check + level derivation), getByBorrowerId
- [ ] Repository: `KycRepository`, `OtpVerificationRepository`
- [ ] Controller: `KycController`
- [ ] Tests (OTP flow, level upgrade logic, expiry, max attempts)

### Phase 4: Loan Product Module (Day 3)

- [ ] Entities: `LoanProduct`, `LoanProductFrequency`
- [ ] Enum: `RepaymentFrequency`
- [ ] DTOs: Create/Update/Response
- [ ] Mapper: `LoanProductMapper`
- [ ] Service: `LoanProductService` — CRUD, soft delete, filtered list
- [ ] Controller: `LoanProductController`
- [ ] Tests

### Phase 5: Loan Application Module (Day 4-5)

- [ ] Entity: `LoanApplication` | Enum: `ApplicationStatus`
- [ ] DTOs: Create/Approve/Reject/Response
- [ ] Mapper: `LoanApplicationMapper`
- [ ] Service: `LoanApplicationService` — apply (7-step validation), approve, reject, getById, list
- [ ] Controller: `LoanApplicationController`
- [ ] Tests (especially validation chain + state transitions)

### Phase 6: Loan + Installment Module (Day 5-7)

- [ ] Entities: `Loan`, `Installment` | Enums: `LoanStatus`, `InstallmentStatus`
- [ ] DTOs: `LoanResponse`, `KfsResponse`, `InstallmentResponse`
- [ ] Mappers: `LoanMapper`, `InstallmentMapper`
- [ ] Helper: `EpiCalculator` (flat interest, rounding, last installment adjustment)
- [ ] Helper: `InstallmentGenerator` (schedule generation)
- [ ] Service: `LoanService` — createFromApplication, getKfs, acceptKfs, getById, list, recalculateStatus
- [ ] Service: `InstallmentService` — getByLoanId
- [ ] Controllers: `LoanController`
- [ ] Tests (EPI calculation, schedule generation, KFS flow)

### Phase 7: Repayment Module (Day 7-8)

- [ ] Entity: `Repayment` | Enums: `PaymentMode`, `PaymentStatus`
- [ ] DTOs: `RepaymentRequest`, `RepaymentResponse`
- [ ] Mapper: `RepaymentMapper`
- [ ] Service: `RepaymentService` — processPayment (FIFO with pessimistic lock), getByLoanId
- [ ] Controller: `RepaymentController`
- [ ] Tests (FIFO logic, partial payments, full payment, prepayment, overpayment rejection, concurrent payments)

### Phase 8: Overdue Scheduler (Day 8-9)

- [ ] Service: `OverdueDetectionService` — detectAndMarkOverdue, processOverdueForLoan
- [ ] Scheduler: `OverdueScheduler` (`@Scheduled`)
- [ ] Tests (mock Clock for date-sensitive tests)

### Phase 9: Events (Day 9)

- [ ] All 19 event classes (see Section 11) — includes OtpGeneratedEvent, OtpVerifiedEvent
- [ ] Publisher calls in each service
- [ ] `EventLogListener` (`@EventListener` — logs all events)
- [ ] Tests

### Phase 10: Polish & Documentation (Day 10)

- [ ] OpenAPI annotations (`@Operation`, `@ApiResponse`, `@Schema`)
- [ ] Postman collection
- [ ] README.md (setup, API overview, architecture)
- [ ] Full integration test: register → KYC → apply → approve → KFS → repay → close
- [ ] Docker Compose (app + PostgreSQL)

---

## 15. Project Structure

```
microloan-platform/
├── src/
│   ├── main/
│   │   ├── java/com/microloan/
│   │   │   ├── MicroloanApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   └── JpaAuditingConfig.java
│   │   │   │
│   │   │   ├── common/
│   │   │   │   ├── exception/
│   │   │   │   │   ├── BusinessException.java
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   ├── InvalidStateException.java
│   │   │   │   │   ├── DuplicateResourceException.java
│   │   │   │   │   └── ErrorCode.java
│   │   │   │   └── handler/
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── BorrowerController.java
│   │   │   │   ├── KycController.java
│   │   │   │   ├── LoanProductController.java
│   │   │   │   ├── LoanApplicationController.java
│   │   │   │   ├── LoanController.java
│   │   │   │   └── RepaymentController.java
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── BorrowerService.java
│   │   │   │   ├── KycService.java
│   │   │   │   ├── LoanProductService.java
│   │   │   │   ├── LoanApplicationService.java
│   │   │   │   ├── LoanService.java
│   │   │   │   ├── InstallmentService.java
│   │   │   │   ├── RepaymentService.java
│   │   │   │   └── OverdueDetectionService.java
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   ├── BorrowerRepository.java
│   │   │   │   ├── KycRepository.java
│   │   │   │   ├── OtpVerificationRepository.java
│   │   │   │   ├── LoanProductRepository.java
│   │   │   │   ├── LoanProductFrequencyRepository.java
│   │   │   │   ├── LoanApplicationRepository.java
│   │   │   │   ├── LoanRepository.java
│   │   │   │   ├── InstallmentRepository.java
│   │   │   │   └── RepaymentRepository.java
│   │   │   │
│   │   │   ├── entity/
│   │   │   │   ├── BaseEntity.java
│   │   │   │   ├── Borrower.java
│   │   │   │   ├── Kyc.java
│   │   │   │   ├── OtpVerification.java
│   │   │   │   ├── LoanProduct.java
│   │   │   │   ├── LoanProductFrequency.java
│   │   │   │   ├── LoanApplication.java
│   │   │   │   ├── Loan.java
│   │   │   │   ├── Installment.java
│   │   │   │   └── Repayment.java
│   │   │   │
│   │   │   ├── dto/
│   │   │   │   ├── common/
│   │   │   │   │   ├── ErrorResponse.java
│   │   │   │   │   └── PagedResponse.java
│   │   │   │   ├── borrower/
│   │   │   │   │   ├── BorrowerCreateRequest.java
│   │   │   │   │   ├── BorrowerUpdateRequest.java
│   │   │   │   │   └── BorrowerResponse.java
│   │   │   │   ├── kyc/
│   │   │   │   │   ├── KycInitiateRequest.java
│   │   │   │   │   ├── KycVerifyOtpRequest.java
│   │   │   │   │   └── KycResponse.java
│   │   │   │   ├── product/
│   │   │   │   │   ├── LoanProductCreateRequest.java
│   │   │   │   │   ├── LoanProductUpdateRequest.java
│   │   │   │   │   └── LoanProductResponse.java
│   │   │   │   ├── application/
│   │   │   │   │   ├── LoanApplicationCreateRequest.java
│   │   │   │   │   ├── ApproveRequest.java
│   │   │   │   │   ├── RejectRequest.java
│   │   │   │   │   └── LoanApplicationResponse.java
│   │   │   │   ├── loan/
│   │   │   │   │   ├── LoanResponse.java
│   │   │   │   │   ├── KfsResponse.java
│   │   │   │   │   └── InstallmentResponse.java
│   │   │   │   └── repayment/
│   │   │   │       ├── RepaymentRequest.java
│   │   │   │       └── RepaymentResponse.java
│   │   │   │
│   │   │   ├── enums/
│   │   │   │   ├── KycLevel.java
│   │   │   │   ├── Gender.java
│   │   │   │   ├── RepaymentFrequency.java
│   │   │   │   ├── ApplicationStatus.java
│   │   │   │   ├── LoanStatus.java
│   │   │   │   ├── InstallmentStatus.java
│   │   │   │   ├── PaymentMode.java
│   │   │   │   └── PaymentStatus.java
│   │   │   │
│   │   │   ├── mapper/
│   │   │   │   ├── BorrowerMapper.java
│   │   │   │   ├── KycMapper.java
│   │   │   │   ├── LoanProductMapper.java
│   │   │   │   ├── LoanApplicationMapper.java
│   │   │   │   ├── LoanMapper.java
│   │   │   │   ├── InstallmentMapper.java
│   │   │   │   └── RepaymentMapper.java
│   │   │   │
│   │   │   ├── helper/
│   │   │   │   ├── EpiCalculator.java
│   │   │   │   └── InstallmentGenerator.java
│   │   │   │
│   │   │   ├── scheduler/
│   │   │   │   └── OverdueScheduler.java
│   │   │   │
│   │   │   └── event/
│   │   │       ├── BorrowerRegisteredEvent.java
│   │   │       ├── KycUpdatedEvent.java
│   │   │       ├── KycLevelUpgradedEvent.java
│   │   │       ├── OtpGeneratedEvent.java
│   │   │       ├── OtpVerifiedEvent.java
│   │   │       ├── LoanApplicationCreatedEvent.java
│   │   │       ├── LoanApplicationApprovedEvent.java
│   │   │       ├── LoanApplicationRejectedEvent.java
│   │   │       ├── LoanCreatedEvent.java
│   │   │       ├── KfsGeneratedEvent.java
│   │   │       ├── KfsAcceptedEvent.java
│   │   │       ├── LoanDisbursedEvent.java
│   │   │       ├── InstallmentsGeneratedEvent.java
│   │   │       ├── RepaymentMadeEvent.java
│   │   │       ├── InstallmentPaidEvent.java
│   │   │       ├── InstallmentOverdueEvent.java
│   │   │       ├── PenaltyAppliedEvent.java
│   │   │       ├── LoanStatusChangedEvent.java
│   │   │       ├── LoanClosedEvent.java
│   │   │       └── EventLogListener.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/
│   │           ├── V1__create_borrower.sql
│   │           ├── V2__create_kyc.sql
│   │           ├── V3__create_otp_verification.sql
│   │           ├── V4__create_loan_product.sql
│   │           ├── V5__create_loan_product_frequency.sql
│   │           ├── V6__create_loan_application.sql
│   │           ├── V7__create_loan.sql
│   │           ├── V8__create_installment.sql
│   │           └── V9__create_repayment.sql
│   │
│   └── test/java/com/microloan/
│       ├── service/
│       │   ├── BorrowerServiceTest.java
│       │   ├── KycServiceTest.java
│       │   ├── LoanProductServiceTest.java
│       │   ├── LoanApplicationServiceTest.java
│       │   ├── LoanServiceTest.java
│       │   ├── RepaymentServiceTest.java
│       │   └── OverdueDetectionServiceTest.java
│       ├── controller/
│       │   ├── BorrowerControllerTest.java
│       │   └── ...
│       └── integration/
│           └── FullLoanLifecycleTest.java
│
├── docker-compose.yml
├── pom.xml
├── README.md
└── PLAN.md
```

---

## 16. Appendices

### A. Flyway Migration Order

| Version | Script | Creates |
|---|---|---|
| V1 | `V1__create_borrower.sql` | `borrower` table + indexes |
| V2 | `V2__create_kyc.sql` | `kyc` table (FK → borrower) |
| V3 | `V3__create_otp_verification.sql` | `otp_verification` (FK → borrower) |
| V4 | `V4__create_loan_product.sql` | `loan_product` table |
| V5 | `V5__create_loan_product_frequency.sql` | `loan_product_frequency` (FK → loan_product) |
| V6 | `V6__create_loan_application.sql` | `loan_application` (FK → borrower, loan_product) |
| V7 | `V7__create_loan.sql` | `loan` (FK → borrower, application, product) |
| V8 | `V8__create_installment.sql` | `installment` (FK → loan) |
| V9 | `V9__create_repayment.sql` | `repayment` (FK → loan) |

### B. Docker Compose (Local Dev)

```yaml
version: '3.8'
services:
  db:
    image: postgres:15-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: microloan_db
      POSTGRES_USER: microloan_user
      POSTGRES_PASSWORD: microloan_pass
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

### C. Key Spring Boot Annotations Reference

| Annotation | Purpose |
|---|---|
| `@RestController` | REST controller |
| `@RequestMapping("/api/v1/...")` | Base path |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | HTTP method |
| `@PathVariable`, `@RequestParam` | Path/query param binding |
| `@RequestBody` | JSON body binding |
| `@Valid` | Triggers bean validation |
| `@Entity`, `@Table` | JPA entity mapping |
| `@Id`, `@GeneratedValue` | Primary key |
| `@Column`, `@Enumerated(EnumType.STRING)` | Column mapping |
| `@ManyToOne`, `@OneToMany`, `@OneToOne` | Relationships |
| `@Transactional` | Transaction boundary |
| `@Transactional(readOnly = true)` | Read-only transaction |
| `@Scheduled` | Cron job |
| `@EventListener` | Event subscriber |
| `@RestControllerAdvice` | Global exception handling |
| `@ExceptionHandler` | Per-exception handler |
| `@Lock(LockModeType.PESSIMISTIC_WRITE)` | SELECT FOR UPDATE |
| `@Schema`, `@Operation`, `@ApiResponse` | OpenAPI/Swagger |
| `@Mapper(componentModel = "spring")` | MapStruct DI integration |
