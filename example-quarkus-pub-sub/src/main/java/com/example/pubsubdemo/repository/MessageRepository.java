package com.example.pubsubdemo.repository;

import com.example.pubsubdemo.entity.EventType;
import com.example.pubsubdemo.entity.Message;
import com.example.pubsubdemo.entity.MessageStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class MessageRepository implements PanacheRepository<Message> {

    public List<Message> findBySource(String source) {
        return list("source", source);
    }

    public List<Message> findByEventType(EventType eventType) {
        return list("eventType", eventType);
    }

    public List<Message> findByStatus(MessageStatus status) {
        return list("status", status);
    }

    public List<Message> findTop10ByCreatedAtDesc() {
        return findAll(Sort.by("createdAt", Sort.Direction.Descending))
                .page(0, 10)
                .list();
    }
}
