CREATE TABLE loan_product (
    product_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(100)  NOT NULL,
    description       TEXT,
    min_principal     DECIMAL(12,2) NOT NULL,
    max_principal     DECIMAL(12,2) NOT NULL,
    min_tenure_months INT           NOT NULL,
    max_tenure_months INT           NOT NULL,
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
