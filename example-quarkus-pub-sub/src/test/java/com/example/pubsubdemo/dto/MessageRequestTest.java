package com.example.pubsubdemo.dto;

import com.example.pubsubdemo.entity.EventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test — nessuna infrastruttura richiesta.
 * Verifica i default del compact constructor del record MessageRequest.
 */
class MessageRequestTest {

    @Test
    void nullSource_defaultsToAPI() {
        MessageRequest req = new MessageRequest("hello", null, EventType.DEMO);
        assertThat(req.source()).isEqualTo("API");
    }

    @Test
    void blankSource_defaultsToAPI() {
        MessageRequest req = new MessageRequest("hello", "   ", EventType.DEMO);
        assertThat(req.source()).isEqualTo("API");
    }

    @Test
    void explicitSource_isPreserved() {
        MessageRequest req = new MessageRequest("hello", "WEB", EventType.DEMO);
        assertThat(req.source()).isEqualTo("WEB");
    }

    @Test
    void nullEventType_defaultsToDemo() {
        MessageRequest req = new MessageRequest("hello", "API", null);
        assertThat(req.eventType()).isEqualTo(EventType.DEMO);
    }

    @Test
    void explicitEventType_isPreserved() {
        MessageRequest req = new MessageRequest("hello", "API", EventType.USER_EVENT);
        assertThat(req.eventType()).isEqualTo(EventType.USER_EVENT);
    }

    @Test
    void text_isPreservedAsIs() {
        MessageRequest req = new MessageRequest("  my text  ", "API", null);
        assertThat(req.text()).isEqualTo("  my text  ");
    }
}
