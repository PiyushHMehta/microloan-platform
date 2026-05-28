CREATE TABLE loan_application (
    application_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id          UUID           NOT NULL,
    product_id           UUID           NOT NULL,
    requested_amount     DECIMAL(12,2)  NOT NULL,
    requested_tenure_months INT         NOT NULL,
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
