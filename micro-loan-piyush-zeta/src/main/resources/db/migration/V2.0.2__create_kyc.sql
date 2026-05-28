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
