package in.zeta.zea_2026_b02_piyushm_microloan.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that transparently encrypts/decrypts {@code String} entity
 * fields using {@link EncryptionService}.
 *
 * <p>Apply to any sensitive column with:
 * <pre>{@code @Convert(converter = EncryptedStringConverter.class)}</pre>
 *
 * <p>The converter delegates to a static {@link EncryptionService} instance registered via
 * {@link #setEncryptionService(EncryptionService)} during Spring context startup. Because
 * Hibernate instantiates converters itself (not through Spring), the static bridge is the
 * standard pattern for accessing Spring-managed state from JPA converters.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile EncryptionService INSTANCE;

    static void setEncryptionService(EncryptionService service) {
        INSTANCE = service;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "EncryptionService is not yet initialized. " +
                    "Ensure app.encryption.key is configured.");
        }
        return INSTANCE.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "EncryptionService is not yet initialized. " +
                    "Ensure app.encryption.key is configured.");
        }
        return INSTANCE.decrypt(ciphertext);
    }
}
