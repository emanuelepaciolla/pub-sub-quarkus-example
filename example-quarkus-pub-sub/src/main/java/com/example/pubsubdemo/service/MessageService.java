package com.example.pubsubdemo.service;

import com.example.pubsubdemo.dto.MessageRequest;
import com.example.pubsubdemo.entity.EventType;
import com.example.pubsubdemo.entity.Message;
import com.example.pubsubdemo.entity.MessageStatus;
import com.example.pubsubdemo.repository.MessageRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class MessageService {

    @Inject
    MessageRepository messageRepository;

    @Transactional
    public Message saveMessage(String text, String source, EventType eventType) {
        Message message = new Message();
        message.text = text;
        message.source = source;
        message.eventType = eventType;
        messageRepository.persist(message);
        Log.infof("Saved message id=%d eventType=%s", message.id, eventType);
        return message;
    }

    @Transactional
    public Message saveMessage(MessageRequest request) {
        return saveMessage(request.text(), request.source(), request.eventType());
    }

    /**
     * Compatibilità con chiamate senza EventType esplicito (default: DEMO).
     */
    @Transactional
    public Message saveMessage(String text, String source) {
        return saveMessage(text, source, EventType.DEMO);
    }

    public List<Message> getAllMessages() {
        return messageRepository.listAll();
    }

    public List<Message> getRecentMessages() {
        return messageRepository.findTop10ByCreatedAtDesc();
    }

    public List<Message> getMessagesBySource(String source) {
        return messageRepository.findBySource(source);
    }

    public List<Message> getMessagesByEventType(EventType eventType) {
        return messageRepository.findByEventType(eventType);
    }

    /**
     * Salva un messaggio proveniente dalla DLQ con status DEAD_LETTERED.
     * Chiamato da DlqSubscriberManager.
     */
    @Transactional
    public Message saveDlqMessage(String text, String source, EventType eventType) {
        Message message = new Message();
        message.text = text;
        message.source = source;
        message.eventType = eventType;
        message.status = MessageStatus.DEAD_LETTERED;
        messageRepository.persist(message);
        Log.warnf("DLQ message saved id=%d eventType=%s source=%s", message.id, eventType, source);
        return message;
    }

    public List<Message> getDlqMessages() {
        return messageRepository.findByStatus(MessageStatus.DEAD_LETTERED);
    }

    public Message getMessageById(Long id) {
        return messageRepository.findByIdOptional(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));
    }

    @Transactional
    public void deleteMessage(Long id) {
        boolean deleted = messageRepository.deleteById(id);
        if (!deleted) {
            throw new RuntimeException("Message not found with id: " + id);
        }
        Log.infof("Deleted message id=%d", id);
    }
}
