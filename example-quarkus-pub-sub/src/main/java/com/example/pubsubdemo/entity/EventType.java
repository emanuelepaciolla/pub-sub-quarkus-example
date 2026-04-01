package com.example.pubsubdemo.entity;

/**
 * Tipi di evento gestiti dalla piattaforma.
 * Ogni valore mappa a una chiave di configurazione (configKey) che referenzia
 * topic e subscription in application.properties sotto gcp.pubsub.*.
 */
public enum EventType {
    DEMO("demo"),
    USER_EVENT("userevent"),
    SYSTEM_EVENT("systemevent"),
    NOTIFICATION("notification");

    /** Chiave usata in application.properties (gcp.pubsub.topics.<configKey>). */
    public final String configKey;

    EventType(String configKey) {
        this.configKey = configKey;
    }

    /** Risolve un EventType dalla config key; ritorna DEMO se sconosciuta. */
    public static EventType fromConfigKey(String key) {
        for (EventType et : values()) {
            if (et.configKey.equals(key)) return et;
        }
        return DEMO;
    }
}
