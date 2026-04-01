package com.example.pubsubdemo.entity;

/**
 * Stato del ciclo di vita di un messaggio.
 * ACTIVE:        elaborato correttamente, presente nel flusso normale.
 * DEAD_LETTERED: non elaborato dopo N tentativi → salvato dalla DLQ subscription.
 */
public enum MessageStatus {
    ACTIVE,
    DEAD_LETTERED
}
