package com.example.pubsubdemo.repository;

import com.example.pubsubdemo.entity.EventType;
import com.example.pubsubdemo.entity.Message;
import com.example.pubsubdemo.entity.MessageStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test — richiede docker-compose up (Postgres).
 *
 * @TestTransaction: ogni test viene eseguito in una transazione
 * che viene automaticamente rollbackata alla fine → stato DB isolato tra i test.
 *
 * Il Pub/Sub emulator NON è richiesto: SubscriberManager e PublisherManager
 * gestiscono gracefully l'assenza dell'emulatore.
 */
@QuarkusTest
@TestTransaction
class MessageRepositoryIT {

    @Inject
    MessageRepository messageRepository;

    /**
     * Pulisce la tabella prima di ogni test.
     * Necessario perché i controller test committano dati senza @TestTransaction,
     * e @TestTransaction fa rollback solo delle operazioni del singolo test method.
     * Questa deleteAll() rientra nella stessa transazione di test → viene rollbackata
     * alla fine del test insieme ai dati inseriti dal test stesso.
     */
    /**
     * Pulisce la tabella prima di ogni test in una transazione separata che committa.
     * REQUIRES_NEW sospende l'eventuale transazione di @TestTransaction (già attiva per
     * il metodo corrente) ed esegue il delete in una transazione indipendente che committa
     * prima che la transazione del test parta. Senza questo, il cleanup sarebbe rolled back
     * insieme ai dati del test stesso.
     */
    @BeforeEach
    @Transactional(value = Transactional.TxType.REQUIRES_NEW)
    void cleanUp() {
        messageRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // findBySource
    // -------------------------------------------------------------------------

    @Test
    void findBySource_returnsOnlyMatchingMessages() {
        persist("testo API", "API", EventType.DEMO);
        persist("testo PUBSUB", "PUBSUB", EventType.DEMO);

        List<Message> result = messageRepository.findBySource("API");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).source).isEqualTo("API");
    }

    @Test
    void findBySource_noMatch_returnsEmptyList() {
        persist("testo", "API", EventType.DEMO);

        List<Message> result = messageRepository.findBySource("NONEXISTENT");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByEventType
    // -------------------------------------------------------------------------

    @Test
    void findByEventType_returnsOnlyMatchingEventType() {
        persist("evento utente", "API", EventType.USER_EVENT);
        persist("evento sistema", "API", EventType.SYSTEM_EVENT);
        persist("notifica", "API", EventType.NOTIFICATION);

        List<Message> userEvents = messageRepository.findByEventType(EventType.USER_EVENT);

        assertThat(userEvents).hasSize(1);
        assertThat(userEvents.get(0).eventType).isEqualTo(EventType.USER_EVENT);
        assertThat(userEvents.get(0).text).isEqualTo("evento utente");
    }

    @Test
    void findByEventType_multipleMatches_returnsAll() {
        persist("demo 1", "API", EventType.DEMO);
        persist("demo 2", "API", EventType.DEMO);
        persist("user", "API", EventType.USER_EVENT);

        List<Message> result = messageRepository.findByEventType(EventType.DEMO);

        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // findTop10ByCreatedAtDesc
    // -------------------------------------------------------------------------

    @Test
    void findTop10ByCreatedAtDesc_limitsToTen() {
        for (int i = 0; i < 15; i++) {
            persist("messaggio " + i, "API", EventType.DEMO);
        }

        List<Message> result = messageRepository.findTop10ByCreatedAtDesc();

        assertThat(result).hasSize(10);
    }

    @Test
    void findTop10ByCreatedAtDesc_withFewMessages_returnsAll() {
        persist("primo", "API", EventType.DEMO);
        persist("secondo", "API", EventType.DEMO);

        List<Message> result = messageRepository.findTop10ByCreatedAtDesc();

        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // persist / findById
    // -------------------------------------------------------------------------

    @Test
    void persist_setsId() {
        Message message = buildMessage("test", "API", EventType.DEMO);
        messageRepository.persist(message);

        assertThat(message.id).isNotNull().isPositive();
    }

    @Test
    void findByIdOptional_existingId_returnsMessage() {
        Message message = buildMessage("trova me", "API", EventType.NOTIFICATION);
        messageRepository.persist(message);

        assertThat(messageRepository.findByIdOptional(message.id))
                .isPresent()
                .get()
                .extracting(m -> m.text)
                .isEqualTo("trova me");
    }

    @Test
    void findByIdOptional_unknownId_returnsEmpty() {
        assertThat(messageRepository.findByIdOptional(Long.MAX_VALUE)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByStatus
    // -------------------------------------------------------------------------

    @Test
    void findByStatus_active_returnsOnlyActiveMessages() {
        Message active = buildMessage("active", "API", EventType.DEMO);
        active.status = MessageStatus.ACTIVE;
        messageRepository.persist(active);

        Message dlq = buildMessage("dlq", "DLQ", EventType.DEMO);
        dlq.status = MessageStatus.DEAD_LETTERED;
        messageRepository.persist(dlq);

        List<Message> result = messageRepository.findByStatus(MessageStatus.ACTIVE);
        assertThat(result).allMatch(m -> m.status == MessageStatus.ACTIVE);
        assertThat(result).contains(active);
        assertThat(result).doesNotContain(dlq);
    }

    @Test
    void findByStatus_deadLettered_returnsOnlyDlqMessages() {
        Message dlq1 = buildMessage("dlq-1", "DLQ", EventType.USER_EVENT);
        dlq1.status = MessageStatus.DEAD_LETTERED;
        messageRepository.persist(dlq1);

        Message dlq2 = buildMessage("dlq-2", "DLQ", EventType.SYSTEM_EVENT);
        dlq2.status = MessageStatus.DEAD_LETTERED;
        messageRepository.persist(dlq2);

        Message active = buildMessage("attivo", "API", EventType.DEMO);
        messageRepository.persist(active);

        List<Message> result = messageRepository.findByStatus(MessageStatus.DEAD_LETTERED);
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(m -> m.status == MessageStatus.DEAD_LETTERED);
    }

    @Test
    void persist_defaultStatus_isActive() {
        Message message = buildMessage("testo", "API", EventType.DEMO);
        messageRepository.persist(message);

        Message found = messageRepository.findById(message.id);
        assertThat(found.status).isEqualTo(MessageStatus.ACTIVE);
    }

    // -------------------------------------------------------------------------
    // helper
    // -------------------------------------------------------------------------

    private void persist(String text, String source, EventType eventType) {
        messageRepository.persist(buildMessage(text, source, eventType));
    }

    private Message buildMessage(String text, String source, EventType eventType) {
        Message m = new Message();
        m.text = text;
        m.source = source;
        m.eventType = eventType;
        return m;
    }
}
