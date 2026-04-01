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

import java.util.ArrayList;
import java.util.List;

/**
 * Avvia un Subscriber GCP Pub/Sub per ogni subscription configurata,
 * instradando i messaggi ricevuti al MessageService con il corretto EventType.
 */
@ApplicationScoped
public class SubscriberManager {

    @Inject
    GcpConfig gcpConfig;

    @Inject
    MessageService messageService;

    @Inject
    RetryPolicyFactory retryPolicyFactory;

    private final List<Subscriber> subscribers = new ArrayList<>();
    private TransportChannelProvider channelProvider;
    private CredentialsProvider credentialsProvider;
    private ManagedChannel channel;

    void onStart(@Observes StartupEvent ev) {
        setupTransport();

        gcpConfig.pubsub().subscriptions().forEach((key, subConfig) -> {
            EventType eventType = EventType.fromConfigKey(key);
            try {
                Subscriber subscriber = buildSubscriber(subConfig.name(), eventType);
                subscriber.startAsync().awaitRunning();
                subscribers.add(subscriber);
                Log.infof("Subscriber avviato per [%s] → subscription: %s (eventType: %s)",
                        key, subConfig.name(), eventType);
            } catch (Exception e) {
                Log.warnf("Impossibile avviare subscriber per [%s] (%s): %s",
                        key, subConfig.name(), e.getMessage());
            }
        });
    }

    void onStop(@Observes ShutdownEvent ev) {
        subscribers.forEach(Subscriber::stopAsync);
        if (channel != null) {
            channel.shutdown();
        }
    }

    private Subscriber buildSubscriber(String subscriptionName, EventType eventType) {
        MessageReceiver receiver = (message, consumer) -> {
            String payload = message.getData().toStringUtf8();
            Log.infof("Messaggio ricevuto su [%s]: %s", eventType, payload);
            try {
                messageService.saveMessage(payload, "PUBSUB", eventType);
                consumer.ack();
            } catch (Exception e) {
                Log.errorf(e, "Errore nell'elaborazione del messaggio [%s] — nack", eventType);
                consumer.nack();
            }
        };

        Subscriber.Builder builder = Subscriber.newBuilder(
                ProjectSubscriptionName.of(gcpConfig.projectId(), subscriptionName),
                receiver);

        builder.setMaxAckExtensionPeriod(retryPolicyFactory.maxAckExtensionDuration());

        if (channelProvider != null) {
            builder.setChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider);
        }

        return builder.build();
    }

    private void setupTransport() {

        String emulatorHost = gcpConfig.pubsub().emulatorHost();

        if (!emulatorHost.isBlank()) {
            Log.infof("SubscriberManager: uso emulatore a %s", emulatorHost);
            channel = ManagedChannelBuilder.forTarget(emulatorHost).usePlaintext().build();
            channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            credentialsProvider = NoCredentialsProvider.create();
        }
    }
}
