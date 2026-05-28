CREATE TABLE loan (
    loan_id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id             UUID           NOT NULL,
    application_id          UUID           UNIQUE,
    product_id              UUID           NOT NULL,
    principal_amount        DECIMAL(12,2)  NOT NULL,
    interest_rate           DECIMAL(5,2)   NOT NULL,
    tenure_months           INT            NOT NULL,
    total_amount            DECIMAL(12,2)  NOT NULL,
    total_penalty_amount    DECIMAL(12,2)  DEFAULT 0,
    total_payable           DECIMAL(12,2)  NOT NULL,
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
