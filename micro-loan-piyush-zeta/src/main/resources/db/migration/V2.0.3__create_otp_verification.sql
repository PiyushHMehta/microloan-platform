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
