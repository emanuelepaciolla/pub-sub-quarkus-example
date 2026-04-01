package com.example.pubsubdemo.service;

import com.example.pubsubdemo.config.GcpConfig;
import com.google.api.gax.retrying.RetrySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.threeten.bp.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test TDD — scritto PRIMA di RetryPolicyFactory.
 * Verifica che il factory costruisca correttamente RetrySettings e Duration
 * a partire dai valori di configurazione.
 * Nessuna infrastruttura richiesta.
 *
 * LENIENT: il helper mockRetryConfig configura tutti i campi per riuso;
 * i test che testano solo maxAckExtensionDuration non usano tutti gli stub.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetryPolicyFactoryTest {

    // -------------------------------------------------------------------------
    // buildRetrySettings
    // -------------------------------------------------------------------------

    @Test
    void buildRetrySettings_appliesAllConfigValues() {
        GcpConfig.PubSub.RetryConfig cfg = mockRetryConfig(3, 200L, 30_000L, 1.5, 120_000L, 60);

        RetrySettings settings = RetryPolicyFactory.buildRetrySettings(cfg);

        assertThat(settings.getMaxAttempts()).isEqualTo(3);
        assertThat(settings.getInitialRetryDelay().toMillis()).isEqualTo(200L);
        assertThat(settings.getMaxRetryDelay().toMillis()).isEqualTo(30_000L);
        assertThat(settings.getRetryDelayMultiplier()).isEqualTo(1.5);
        assertThat(settings.getTotalTimeout().toMillis()).isEqualTo(120_000L);
    }

    @Test
    void buildRetrySettings_withDefaultValues_doesNotThrow() {
        GcpConfig.PubSub.RetryConfig cfg = mockRetryConfig(5, 100L, 60_000L, 2.0, 600_000L, 60);

        assertThatCode(() -> RetryPolicyFactory.buildRetrySettings(cfg))
                .doesNotThrowAnyException();
    }

    @Test
    void buildRetrySettings_maxAttemptsOne_meansNoRetry() {
        GcpConfig.PubSub.RetryConfig cfg = mockRetryConfig(1, 100L, 1_000L, 2.0, 5_000L, 30);

        RetrySettings settings = RetryPolicyFactory.buildRetrySettings(cfg);

        assertThat(settings.getMaxAttempts()).isEqualTo(1);
    }

    @Test
    void buildRetrySettings_multiplierHigherThanOne_appliedCorrectly() {
        GcpConfig.PubSub.RetryConfig cfg = mockRetryConfig(5, 100L, 60_000L, 3.0, 300_000L, 60);

        RetrySettings settings = RetryPolicyFactory.buildRetrySettings(cfg);

        assertThat(settings.getRetryDelayMultiplier()).isEqualTo(3.0);
    }

    // -------------------------------------------------------------------------
    // maxAckExtensionDuration
    // -------------------------------------------------------------------------

    @Test
    void maxAckExtensionDuration_returnsConfiguredSeconds() {
        GcpConfig.PubSub.RetryConfig cfg = mockRetryConfig(5, 100L, 60_000L, 2.0, 600_000L, 120);

        Duration duration = RetryPolicyFactory.maxAckExtensionDuration(cfg);

        assertThat(duration.getSeconds()).isEqualTo(120);
    }

    @Test
    void maxAckExtensionDuration_zeroSeconds_returnsZeroDuration() {
        GcpConfig.PubSub.RetryConfig cfg = mockRetryConfig(5, 100L, 60_000L, 2.0, 600_000L, 0);

        Duration duration = RetryPolicyFactory.maxAckExtensionDuration(cfg);

        assertThat(duration.isZero()).isTrue();
    }

    // -------------------------------------------------------------------------
    // helper
    // -------------------------------------------------------------------------

    private GcpConfig.PubSub.RetryConfig mockRetryConfig(
            int maxAttempts, long initialMs, long maxMs,
            double multiplier, long totalMs, int maxAckExtSec) {
        GcpConfig.PubSub.RetryConfig cfg = mock(GcpConfig.PubSub.RetryConfig.class);
        when(cfg.maxAttempts()).thenReturn(maxAttempts);
        when(cfg.initialBackoffMs()).thenReturn(initialMs);
        when(cfg.maxBackoffMs()).thenReturn(maxMs);
        when(cfg.backoffMultiplier()).thenReturn(multiplier);
        when(cfg.totalTimeoutMs()).thenReturn(totalMs);
        when(cfg.maxAckExtensionSeconds()).thenReturn(maxAckExtSec);
        return cfg;
    }
}
