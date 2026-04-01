package com.example.pubsubdemo.service;

import com.example.pubsubdemo.config.GcpConfig;
import com.example.pubsubdemo.entity.EventType;
import com.google.api.core.ApiFuture;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gestisce un Publisher GCP Pub/Sub per ogni topic configurato.
 * Viene inizializzato all'avvio dell'applicazione.
 */
@ApplicationScoped
public class PublisherManager {

    @Inject
    GcpConfig gcpConfig;

    @Inject
    RetryPolicyFactory retryPolicyFactory;

    private final Map<String, Publisher> publishers = new ConcurrentHashMap<>();
    private TransportChannelProvider channelProvider;
    private CredentialsProvider credentialsProvider;
    private ManagedChannel channel;

    void onStart(@Observes StartupEvent ev) {
        setupTransport();

        gcpConfig.pubsub().topics().forEach((key, topicName) -> {
            try {
                Publisher.Builder builder =
                        Publisher.newBuilder(TopicName.of(gcpConfig.projectId(), topicName))
                                .setRetrySettings(retryPolicyFactory.buildPublisherRetrySettings());
                if (channelProvider != null) {
                    builder.setChannelProvider(channelProvider)
                            .setCredentialsProvider(credentialsProvider);
                }
                Publisher publisher = builder.build();
                publishers.put(key, publisher);
                Log.infof("Publisher pronto per [%s] → topic: %s", key, topicName);
            } catch (Exception e) {
                Log.warnf("Impossibile creare publisher per [%s]: %s", key, e.getMessage());
            }
        });
    }

    void onStop(@Observes ShutdownEvent ev) {
        publishers.values().forEach(Publisher::shutdown);
        if (channel != null) {
            channel.shutdown();
        }
    }

    /**
     * Pubblica un messaggio sul topic corrispondente all'EventType.
     *
     * @return messageId assegnato da Pub/Sub
     */
    public String publish(EventType eventType, String data, Map<String, String> attributes) {
        Publisher publisher = publishers.get(eventType.configKey);
        if (publisher == null) {
            throw new IllegalStateException(
                    "Nessun publisher disponibile per l'event type: " + eventType);
        }
        try {
            PubsubMessage.Builder builder = PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(data))
                    // Aggiunto per tracciabilità DLQ: il DlqSubscriberManager lo legge
                    // per riassociare il messaggio dead-lettered al suo EventType originale.
                    .putAttributes("eventType", eventType.name());
            if (attributes != null) {
                attributes.forEach(builder::putAttributes);
            }
            ApiFuture<String> future = publisher.publish(builder.build());
            String messageId = future.get(5, TimeUnit.SECONDS);
            Log.infof("Pubblicato [%s] messageId=%s", eventType, messageId);
            return messageId;
        } catch (Exception e) {
            Log.errorf(e, "Errore nella pubblicazione su [%s]", eventType);
            throw new RuntimeException("Failed to publish message", e);
        }
    }

    private void setupTransport() {
        String emulatorHost = gcpConfig.pubsub().emulatorHost();
        if (!emulatorHost.isBlank()) {
            Log.infof("PublisherManager: uso emulatore a %s", emulatorHost);
            channel = ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
            channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            credentialsProvider = NoCredentialsProvider.create();
        } else {
            // In produzione: credenziali ADC, transport default
            channelProvider = null;
            credentialsProvider = null;
        }
    }
}
