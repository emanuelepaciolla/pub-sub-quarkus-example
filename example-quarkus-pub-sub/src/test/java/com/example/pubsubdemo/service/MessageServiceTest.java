package com.example.pubsubdemo.service;

import com.example.pubsubdemo.dto.MessageRequest;
import com.example.pubsubdemo.entity.EventType;
import com.example.pubsubdemo.entity.Message;
import com.example.pubsubdemo.entity.MessageStatus;
import com.example.pubsubdemo.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test — nessuna infrastruttura richiesta.
 * Il repository è mockato: si testa la sola logica del service.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    MessageRepository messageRepository;

    @InjectMocks
    MessageService messageService;

    /** Stub del persist che simula l'assegnazione dell'id da parte di Hibernate. */
    private void stubPersist() {
        doAnswer(inv -> {
            Message msg = inv.getArgument(0);
            msg.id = 1L;
            return null;
        }).when(messageRepository).persist(any(Message.class));
    }

    // -------------------------------------------------------------------------
    // saveMessage
    // -------------------------------------------------------------------------

    @Test
    void saveMessage_withAllFields_persistsCorrectly() {
        stubPersist();
        Message result = messageService.saveMessage("ciao", "API", EventType.USER_EVENT);

        assertThat(result.text).isEqualTo("ciao");
        assertThat(result.source).isEqualTo("API");
        assertThat(result.eventType).isEqualTo(EventType.USER_EVENT);
        assertThat(result.id).isEqualTo(1L);
        verify(messageRepository).persist(any(Message.class));
    }

    @Test
    void saveMessage_withRequest_usesRequestFields() {
        stubPersist();
        MessageRequest request = new MessageRequest("testo", "WEB", EventType.NOTIFICATION);
        Message result = messageService.saveMessage(request);

        assertThat(result.text).isEqualTo("testo");
        assertThat(result.source).isEqualTo("WEB");
        assertThat(result.eventType).isEqualTo(EventType.NOTIFICATION);
    }

    @Test
    void saveMessage_withoutEventType_defaultsToDemo() {
        stubPersist();
        Message result = messageService.saveMessage("testo", "API");

        assertThat(result.eventType).isEqualTo(EventType.DEMO);
    }

    // -------------------------------------------------------------------------
    // getAllMessages / getRecentMessages / getMessagesBySource / getMessagesByEventType
    // -------------------------------------------------------------------------

    @Test
    void getAllMessages_delegatesToRepository() {
        when(messageRepository.listAll()).thenReturn(List.of(new Message(), new Message()));

        assertThat(messageService.getAllMessages()).hasSize(2);
        verify(messageRepository).listAll();
    }

    @Test
    void getRecentMessages_delegatesToRepository() {
        when(messageRepository.findTop10ByCreatedAtDesc()).thenReturn(List.of(new Message()));

        assertThat(messageService.getRecentMessages()).hasSize(1);
        verify(messageRepository).findTop10ByCreatedAtDesc();
    }

    @Test
    void getMessagesBySource_delegatesToRepository() {
        when(messageRepository.findBySource("PUBSUB")).thenReturn(List.of(new Message()));

        messageService.getMessagesBySource("PUBSUB");

        verify(messageRepository).findBySource("PUBSUB");
    }

    @Test
    void getMessagesByEventType_delegatesToRepository() {
        when(messageRepository.findByEventType(EventType.SYSTEM_EVENT)).thenReturn(List.of());

        messageService.getMessagesByEventType(EventType.SYSTEM_EVENT);

        verify(messageRepository).findByEventType(EventType.SYSTEM_EVENT);
    }

    // -------------------------------------------------------------------------
    // getMessageById
    // -------------------------------------------------------------------------

    @Test
    void getMessageById_whenFound_returnsMessage() {
        Message message = new Message();
        message.id = 42L;
        when(messageRepository.findByIdOptional(42L)).thenReturn(Optional.of(message));

        assertThat(messageService.getMessageById(42L).id).isEqualTo(42L);
    }

    @Test
    void getMessageById_whenNotFound_throwsRuntimeException() {
        when(messageRepository.findByIdOptional(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.getMessageById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // deleteMessage
    // -------------------------------------------------------------------------

    @Test
    void deleteMessage_callsDeleteById() {
        when(messageRepository.deleteById(5L)).thenReturn(true);

        messageService.deleteMessage(5L);

        verify(messageRepository).deleteById(5L);
    }

    @Test
    void deleteMessage_notFound_throwsRuntimeException() {
        when(messageRepository.deleteById(99L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.deleteMessage(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // saveDlqMessage / getDlqMessages
    // -------------------------------------------------------------------------

    @Test
    void saveDlqMessage_persistsWithDeadLetteredStatus() {
        stubPersist();
        Message result = messageService.saveDlqMessage("errore", "DLQ", EventType.USER_EVENT);

        assertThat(result.status).isEqualTo(MessageStatus.DEAD_LETTERED);
        assertThat(result.eventType).isEqualTo(EventType.USER_EVENT);
        assertThat(result.source).isEqualTo("DLQ");
        verify(messageRepository).persist(any(Message.class));
    }

    @Test
    void saveDlqMessage_doesNotSetActiveStatus() {
        stubPersist();
        Message result = messageService.saveDlqMessage("errore", "DLQ", EventType.DEMO);

        assertThat(result.status).isNotEqualTo(MessageStatus.ACTIVE);
    }

    @Test
    void getDlqMessages_delegatesToRepositoryWithDeadLetteredStatus() {
        when(messageRepository.findByStatus(MessageStatus.DEAD_LETTERED)).thenReturn(List.of());

        messageService.getDlqMessages();

        verify(messageRepository).findByStatus(MessageStatus.DEAD_LETTERED);
    }
}
