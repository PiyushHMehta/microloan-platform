package in.zeta.zea_2026_b02_piyushm_microloan.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256/CBC/PKCS5Padding field-level encryption service.
 *
 * <p>Uses a <strong>deterministic</strong> IV (first 16 bytes of the key) so that
 * equal plaintexts produce equal ciphertexts. This preserves DB-level UNIQUE
 * constraints and allows equality-based repository queries on encrypted columns
 * (e.g. {@code findByPanNumber}, {@code findByPhoneNumber}).
 *
 * <p>Key is provided via {@code app.encryption.key} as a Base64-encoded 32-byte value.
 * Generate one with: {@code openssl rand -base64 32}
 */
@Component
public class EncryptionService {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private final SecretKeySpec keySpec;
    private final IvParameterSpec ivSpec;

    public EncryptionService(@Value("${app.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "app.encryption.key must decode to exactly 32 bytes (AES-256). " +
                    "Generate one with: openssl rand -base64 32");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        // Deterministic IV: first 16 bytes of the key
        this.ivSpec = new IvParameterSpec(Arrays.copyOf(keyBytes, 16));
    }

    @PostConstruct
    void registerWithConverter() {
        EncryptedStringConverter.setEncryptionService(this);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
