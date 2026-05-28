-- =========================================================================
-- V2.1.1 — Widen PII columns for AES-256 encrypted values
--
-- Fields encrypted with AES-256/CBC/PKCS5Padding produce Base64 output that
-- is significantly longer than the original plaintext. New column widths are
-- calculated as: ceil(ceil(maxPlaintextBytes / 16) * 16 / 3) * 4 (Base64).
--
-- Format-check constraints on pan_number and aadhaar_number are dropped
-- because the encrypted values are Base64 strings and no longer match the
-- original PAN/Aadhaar regex patterns.
--
-- The idx_borrower_city index is dropped — LIKE/partial searches on an
-- encrypted column are meaningless; equality lookups can still use seqscan.
-- =========================================================================

-- ----- kyc -----
ALTER TABLE kyc
    ALTER COLUMN pan_number     TYPE VARCHAR(64),
    ALTER COLUMN aadhaar_number TYPE VARCHAR(64);

DROP INDEX IF EXISTS idx_borrower_city;

ALTER TABLE kyc
    DROP CONSTRAINT IF EXISTS chk_pan_format,
    DROP CONSTRAINT IF EXISTS chk_aadhaar_format;

-- ----- borrower -----
ALTER TABLE borrower
    ALTER COLUMN full_name      TYPE VARCHAR(256),
    ALTER COLUMN phone_number   TYPE VARCHAR(64),
    ALTER COLUMN email          TYPE VARCHAR(256),
    ALTER COLUMN address_line1  TYPE VARCHAR(512),
    ALTER COLUMN address_line2  TYPE VARCHAR(512),
    ALTER COLUMN city           TYPE VARCHAR(256),
    ALTER COLUMN state          TYPE VARCHAR(256),
    ALTER COLUMN pincode        TYPE VARCHAR(64);
