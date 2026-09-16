package com.kdh.solvego.domain.subscription.billing;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BillingKeyCipherTest {
    static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test void roundTripUsesFreshNonceAndAuthenticatedCustomer() {
        BillingKeyCipher cipher = new BillingKeyCipher(randomKey());
        String encrypted = cipher.encrypt("billing-sensitive", "customer-a");
        assertThat(encrypted).doesNotContain("billing-sensitive");
        assertThat(encrypted).isNotEqualTo(cipher.encrypt("billing-sensitive", "customer-a"));
        assertThat(cipher.decrypt(encrypted, "customer-a")).isEqualTo("billing-sensitive");
        assertThatThrownBy(() -> cipher.decrypt(encrypted, "customer-b")).isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(() -> new BillingKeyCipher(randomKey()).decrypt(encrypted, "customer-a"))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[15] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(bytes), "customer-a"))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test void rejectsInvalidKeysAndCiphertextWithoutLeakingValues() {
        assertThatThrownBy(() -> new BillingKeyCipher("secret-not-base64"))
                .hasMessage("Billing encryption key must be base64-encoded 32 bytes").hasNoCause();
        BillingKeyCipher cipher = new BillingKeyCipher(randomKey());
        for (String value : new String[]{"v2:bad", "v1:!", "v1:AA=="}) {
            assertThatThrownBy(() -> cipher.decrypt(value, "customer"))
                    .hasMessage("Billing ciphertext authentication failed").hasNoCause();
        }
        assertThat(new BillingKeyCipher("").isConfigured()).isFalse();
        assertThatThrownBy(() -> new BillingKeyCipher("").encrypt("billing", "customer"))
                .isInstanceOf(IllegalStateException.class);
    }
}
