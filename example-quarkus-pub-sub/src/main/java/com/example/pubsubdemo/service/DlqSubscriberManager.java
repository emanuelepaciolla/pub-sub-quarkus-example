package com.example.pubsubdemo.service;

import com.example.pubsubdemo.config.GcpConfig;
import com.example.pubsubdemo.entity.EventType;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Subscriber dedicato alla Dead Letter Queue.
 *
 * Riceve i messaggi che non sono stati elaborati dopo maxDeliveryAttempts
 * tentativi e li salva in DB con status = DEAD_LETTERED.
 *
 * Regola fondamentale: i messaggi DLQ vengono SEMPRE ackati — un nack
 * causerebbe un loop infinito sulla stessa DLQ subscription.
 *
 * Come funziona la DLQ in GCP Pub/Sub:
 *   1. La subscription normale è configurata con una deadLetterPolicy
 *      che punta a un "dead letter topic" e specifica maxDeliveryAttempts.
 *   2. Dopo N nack (o scadenze ack), Pub/Sub sposta automaticamente il messaggio
 *      sul dead letter topic. NON è il client a farlo.
 *   3. Questo subscriber consuma il dead letter topic e registra i messaggi.
 */
@ApplicationScoped
public class DlqSubscriberManager {

    @Inject
    GcpConfig gcpConfig;

    @Inject
    MessageService messageService;

    @Inject
    RetryPolicyFactory retryPolicyFactory;

    private Subscriber subscriber;

    private ManagedChannel channel;

    void onStart(@Observes StartupEvent ev) {
        String subscriptionName = gcpConfig.pubsub().dlq().subscriptionName();
        String emulatorHost = gcpConfig.pubsub().emulatorHost();

        Subscriber.Builder builder = Subscriber.newBuilder(
                ProjectSubscriptionName.of(gcpConfig.projectId(), subscriptionName),
                buildDlqReceiver());

        builder.setMaxAckExtensionPeriod(retryPolicyFactory.maxAckExtensionDuration());

        if (!emulatorHost.isBlank()) {
            Log.infof("DlqSubscriberManager: uso emulatore a %s", emulatorHost);
            channel = ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
            TransportChannelProvider channelProvider =
                    FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            CredentialsProvider credentialsProvider = NoCredentialsProvider.create();
            builder.setChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider);
        }

        try {
            subscriber = builder.build();
            subscriber.startAsync().awaitRunning();
            Log.infof("DLQ subscriber avviato — subscription: %s", subscriptionName);
        } catch (Exception e) {
            Log.warnf("DLQ subscriber non avviato (DLQ disabilitata): %s", e.getMessage());
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (subscriber != null) {
            subscriber.stopAsync();
        }
        if (channel != null) {
            channel.shutdown();
        }
    }

    private MessageReceiver buildDlqReceiver() {
        return (message, consumer) -> {
            String payload = message.getData().toStringUtf8();
            Map<String, String> attrs = message.getAttributesMap();
            EventType eventType = extractEventType(attrs);

            Log.warnf("Messaggio DLQ ricevuto — eventType=%s, tentativi=%s, payload=%s",
                    eventType,
                    attrs.getOrDefault("CloudPubSubDeadLetterSourceSubscriptionRetries", "?"),
                    payload);

            try {
                messageService.saveDlqMessage(payload, "DLQ", eventType);
            } catch (Exception e) {
                Log.errorf(e, "Errore salvataggio messaggio DLQ — ack comunque per evitare loop");
            }

            // Sempre ACK: non si nackano messaggi sulla DLQ
            consumer.ack();
        };
    }

    /**
     * Estrae l'EventType dagli attributi del messaggio.
     * Il publisher aggiunge l'attributo "eventType" ad ogni messaggio pubblicato.
     * Se assente (messaggio esterno), ritorna DEMO come fallback.
     */
    private EventType extractEventType(Map<String, String> attributes) {
        String attr = attributes.get("eventType");
        if (attr == null) return EventType.DEMO;
        try {
            return EventType.valueOf(attr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EventType.fromConfigKey(attr.toLowerCase());
        }
    }
}
