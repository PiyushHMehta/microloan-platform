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
