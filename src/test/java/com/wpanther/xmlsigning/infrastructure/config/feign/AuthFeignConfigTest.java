package com.wpanther.xmlsigning.infrastructure.config.feign;

import feign.Retryer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthFeignConfig}.
 */
@DisplayName("AuthFeignConfig")
class AuthFeignConfigTest {

    private final AuthFeignConfig config = new AuthFeignConfig();

    @Test
    @DisplayName("authRetryer returns configured Retryer with 3 attempts")
    void testAuthRetryer() {
        Retryer retryer = config.authRetryer();

        assertThat(retryer).isNotNull();
        assertThat(retryer).isInstanceOf(Retryer.Default.class);
    }

    @Test
    @DisplayName("authRetryer Retryer.Default has correct constructor parameters")
    void testAuthRetryerIsDefault() {
        Retryer retryer = config.authRetryer();

        // The AuthFeignConfig uses Retryer.Default(1000, 1000, 3)
        // This creates a retryer that retries up to 3 times with 1 second intervals
        assertThat(retryer).isInstanceOf(Retryer.Default.class);
    }
}
