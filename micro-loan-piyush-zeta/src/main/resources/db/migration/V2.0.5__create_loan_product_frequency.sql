CREATE TABLE loan_product_frequency (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id   UUID        NOT NULL,
    frequency    VARCHAR(20) NOT NULL,

    CONSTRAINT fk_freq_product FOREIGN KEY (product_id)
        REFERENCES loan_product(product_id) ON DELETE CASCADE,
    CONSTRAINT uq_product_frequency UNIQUE (product_id, frequency),
    CONSTRAINT chk_frequency CHECK (frequency IN ('WEEKLY', 'BIWEEKLY', 'MONTHLY'))
);
