package com.example.pubsubdemo.service;

import com.example.pubsubdemo.config.GcpConfig;
import com.google.api.gax.retrying.RetrySettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.threeten.bp.Duration;

/**
 * Factory per la costruzione delle retry policy GCP Pub/Sub.
 *
 * I metodi statici sono testabili senza CDI (vedi RetryPolicyFactoryTest).
 * I metodi pubblici non-statici usano la configurazione iniettata per
 * costruire le policy da passare a Publisher e Subscriber builder.
 */
@ApplicationScoped
public class RetryPolicyFactory {

    @Inject
    GcpConfig gcpConfig;

    /**
     * RetrySettings da usare nel {@code Publisher.Builder}.
     */
    public RetrySettings buildPublisherRetrySettings() {
        return buildRetrySettings(gcpConfig.pubsub().retry());
    }

    /**
     * Durata massima di estensione dell'ack deadline per i subscriber.
     */
    public Duration maxAckExtensionDuration() {
        return maxAckExtensionDuration(gcpConfig.pubsub().retry());
    }

    // -------------------------------------------------------------------------
    // Metodi statici — package-visible per i test
    // -------------------------------------------------------------------------

    static RetrySettings buildRetrySettings(GcpConfig.PubSub.RetryConfig cfg) {
        return RetrySettings.newBuilder()
                .setMaxAttempts(cfg.maxAttempts())
                .setInitialRetryDelay(Duration.ofMillis(cfg.initialBackoffMs()))
                .setMaxRetryDelay(Duration.ofMillis(cfg.maxBackoffMs()))
                .setRetryDelayMultiplier(cfg.backoffMultiplier())
                .setTotalTimeout(Duration.ofMillis(cfg.totalTimeoutMs()))
                .build();
    }

    static Duration maxAckExtensionDuration(GcpConfig.PubSub.RetryConfig cfg) {
        return Duration.ofSeconds(cfg.maxAckExtensionSeconds());
    }
}
