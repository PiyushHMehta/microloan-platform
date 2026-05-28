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
