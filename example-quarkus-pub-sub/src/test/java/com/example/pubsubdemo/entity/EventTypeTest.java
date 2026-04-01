package com.example.pubsubdemo.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test — nessuna infrastruttura richiesta.
 */
class EventTypeTest {

    @Test
    void fromConfigKey_knownKeys_returnCorrectType() {
        assertThat(EventType.fromConfigKey("demo")).isEqualTo(EventType.DEMO);
        assertThat(EventType.fromConfigKey("userevent")).isEqualTo(EventType.USER_EVENT);
        assertThat(EventType.fromConfigKey("systemevent")).isEqualTo(EventType.SYSTEM_EVENT);
        assertThat(EventType.fromConfigKey("notification")).isEqualTo(EventType.NOTIFICATION);
    }

    @Test
    void fromConfigKey_unknownKey_fallsBackToDemo() {
        assertThat(EventType.fromConfigKey("nonexistent")).isEqualTo(EventType.DEMO);
        assertThat(EventType.fromConfigKey("")).isEqualTo(EventType.DEMO);
    }

    @Test
    void allEnumValues_haveUniqueConfigKeys() {
        Set<String> keys = Arrays.stream(EventType.values())
                .map(et -> et.configKey)
                .collect(Collectors.toSet());

        assertThat(keys).hasSameSizeAs(EventType.values());
    }

    @Test
    void allEnumValues_haveNonBlankConfigKey() {
        for (EventType et : EventType.values()) {
            assertThat(et.configKey)
                    .as("configKey di %s non deve essere blank", et)
                    .isNotBlank();
        }
    }

    @Test
    void fromConfigKey_isInverseOfConfigKey() {
        for (EventType et : EventType.values()) {
            assertThat(EventType.fromConfigKey(et.configKey)).isEqualTo(et);
        }
    }
}
