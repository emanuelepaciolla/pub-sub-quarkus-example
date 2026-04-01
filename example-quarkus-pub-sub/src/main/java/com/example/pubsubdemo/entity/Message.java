package com.example.pubsubdemo.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, length = 1000)
    public String text;

    @Column(name = "source")
    public String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    public EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    public MessageStatus status = MessageStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;
}
