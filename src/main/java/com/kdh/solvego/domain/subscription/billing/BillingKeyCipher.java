package com.kdh.solvego.domain.subscription.billing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM; random 96-bit nonce, 128-bit tag, customer-bound authenticated data. */
@Component
public class BillingKeyCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public BillingKeyCipher(@Value("${payment.billing-encryption-key:}") String encodedKey) {
        try {
            byte[] bytes = encodedKey.isBlank() ? null : Base64.getDecoder().decode(encodedKey);
            if (bytes != null && bytes.length != 32) throw new IllegalArgumentException();
            key = bytes == null ? null : new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Billing encryption key must be base64-encoded 32 bytes");
        }
    }

    public boolean isConfigured() { return key != null; }

    public String encrypt(String plaintext, String customerKey) {
        if (key == null) throw new IllegalStateException("Billing encryption is not configured");
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, customerKey);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Billing encryption failed");
        }
    }

    public String decrypt(String value, String customerKey) {
        if (key == null) throw new IllegalStateException("Billing encryption is not configured");
        try {
            if (!value.startsWith("v1:")) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            if (buffer.remaining() < 28) throw new IllegalArgumentException();
            byte[] nonce = new byte[12];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            return new String(cipher(Cipher.DECRYPT_MODE, nonce, customerKey).doFinal(encrypted),
                    StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Billing ciphertext authentication failed");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String customerKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(("solvego:billing:v1:" + customerKey).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
