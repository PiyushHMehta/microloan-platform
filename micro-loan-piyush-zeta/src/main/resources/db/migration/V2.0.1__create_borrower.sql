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
