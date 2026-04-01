package com.example.pubsubdemo.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;

/**
 * Mapping tipizzato della configurazione GCP.
 *
 * Struttura in application.properties:
 *   gcp.project-id=...
 *   gcp.pubsub.emulator-host=...
 *   gcp.pubsub.topics.<key>=<topic-name>
 *   gcp.pubsub.subscriptions.<key>.name=<subscription-name>
 *   gcp.pubsub.subscriptions.<key>.topic=<topic-name>
 *   gcp.pubsub.dlq.topic-name=<dlq-topic>
 *   gcp.pubsub.dlq.subscription-name=<dlq-sub>
 *   gcp.pubsub.dlq.max-delivery-attempts=<N>
 *   gcp.pubsub.retry.max-attempts=<N>
 *   gcp.pubsub.retry.initial-backoff-ms=<ms>
 *   gcp.pubsub.retry.max-backoff-ms=<ms>
 *   gcp.pubsub.retry.backoff-multiplier=<double>
 *   gcp.pubsub.retry.total-timeout-ms=<ms>
 *   gcp.pubsub.retry.max-ack-extension-seconds=<s>
 */
@ConfigMapping(prefix = "gcp")
public interface GcpConfig {

    @WithName("project-id")
    String projectId();

    PubSub pubsub();

    interface PubSub {

        /** Host dell'emulatore Pub/Sub. Vuoto = usa GCP reale. */
        @WithDefault("")
        String emulatorHost();

        /** Mappa configKey → nome del topic GCP. */
        Map<String, String> topics();

        /** Mappa configKey → configurazione della subscription. */
        Map<String, SubscriptionConfig> subscriptions();

        /** Configurazione della Dead Letter Queue. */
        Dlq dlq();

        /** Configurazione della retry policy per publisher e subscriber. */
        RetryConfig retry();

        interface SubscriptionConfig {
            String name();
            String topic();
        }

        interface Dlq {
            /** Nome del topic GCP che riceve i messaggi dead-lettered. */
            @WithDefault("messages-dlq")
            String topicName();

            /** Nome della subscription che consuma il topic DLQ. */
            @WithDefault("messages-dlq-sub")
            String subscriptionName();

            /**
             * Numero massimo di tentativi di consegna prima di mandare
             * il messaggio in DLQ. Usato nell'init dell'emulatore.
             */
            @WithDefault("5")
            int maxDeliveryAttempts();
        }

        interface RetryConfig {
            /** Numero massimo di tentativi (incluso il primo). */
            @WithDefault("5")
            int maxAttempts();

            /** Attesa iniziale fra i tentativi in millisecondi. */
            @WithDefault("100")
            long initialBackoffMs();

            /** Attesa massima fra i tentativi in millisecondi. */
            @WithDefault("60000")
            long maxBackoffMs();

            /** Moltiplicatore applicato ad ogni retry. */
            @WithDefault("2.0")
            double backoffMultiplier();

            /** Timeout totale per tutti i tentativi in millisecondi. */
            @WithDefault("600000")
            long totalTimeoutMs();

            /**
             * Durata massima in secondi per cui il subscriber estende
             * il deadline di ack. 0 = usa il default di Pub/Sub SDK.
             */
            @WithDefault("60")
            int maxAckExtensionSeconds();
        }
    }
}
