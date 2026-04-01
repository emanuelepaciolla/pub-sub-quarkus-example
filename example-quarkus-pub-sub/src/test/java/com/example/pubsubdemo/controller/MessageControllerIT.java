package com.example.pubsubdemo.controller;

import com.example.pubsubdemo.entity.EventType;
import com.example.pubsubdemo.service.PublisherManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Integration test — richiede docker-compose up (Postgres).
 * Il Pub/Sub emulator NON è richiesto: PublisherManager è mockato con @InjectMock.
 *
 * Strategia di isolamento: ogni test crea i propri dati tramite REST
 * e verifica solo le entity create in quel test (via id restituito).
 * Non serve cleanup tra test.
 */
@QuarkusTest
@TestSecurity(user = "admin", roles = {"ADMIN", "PUBLISHER", "READER"})
class MessageControllerIT {

    /** Mock del PublisherManager: evita connessioni reali a GCP / emulatore. */
    @InjectMock
    PublisherManager publisherManager;

    // =========================================================================
    // POST /messages — creazione diretta su DB
    // =========================================================================

    @Test
    void createMessage_validRequest_returns201WithBody() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "messaggio di test", "eventType": "DEMO"}
                        """)
                .when().post("/messages")
                .then()
                .statusCode(201)
                .body("text", is("messaggio di test"))
                .body("source", is("API"))
                .body("eventType", is("DEMO"))
                .body("id", notNullValue());
    }

    @Test
    void createMessage_nullText_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "", "eventType": "DEMO"}
                        """)
                .when().post("/messages")
                .then()
                .statusCode(400);
    }

    @Test
    void createMessage_nullEventType_defaultsToDemo() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "senza eventType"}
                        """)
                .when().post("/messages")
                .then()
                .statusCode(201)
                .body("eventType", is("DEMO"));
    }

    // =========================================================================
    // GET /messages/{id}
    // =========================================================================

    @Test
    void getById_existingId_returns200() {
        Long id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "cerca per id", "eventType": "USER_EVENT"}
                        """)
                .post("/messages")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");

        given()
                .when().get("/messages/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id.intValue()))
                .body("eventType", is("USER_EVENT"));
    }

    @Test
    void getById_nonExistingId_returns404() {
        given()
                .when().get("/messages/999999999")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    // =========================================================================
    // GET /messages/type/{eventType}
    // =========================================================================

    @Test
    void getByEventType_validType_returnsMatchingMessages() {
        // Creo un messaggio con NOTIFICATION
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "notifica importante", "eventType": "NOTIFICATION"}
                        """)
                .post("/messages")
                .then().statusCode(201);

        // Verifico che appaia nel filtro per NOTIFICATION
        given()
                .when().get("/messages/type/NOTIFICATION")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("eventType", everyItem(is("NOTIFICATION")));
    }

    @Test
    void getByEventType_invalidType_returns400WithValidValues() {
        given()
                .when().get("/messages/type/INVALID_TYPE")
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("validi", hasItems("DEMO", "USER_EVENT", "SYSTEM_EVENT", "NOTIFICATION"));
    }

    @Test
    void getByEventType_caseInsensitive_works() {
        given()
                .when().get("/messages/type/demo")
                .then()
                .statusCode(200);
    }

    // =========================================================================
    // GET /messages/source/{source}
    // =========================================================================

    @Test
    void getBySource_returnsMessagesWithMatchingSource() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "da API", "source": "API", "eventType": "DEMO"}
                        """)
                .post("/messages")
                .then().statusCode(201);

        given()
                .when().get("/messages/source/API")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("source", everyItem(is("API")));
    }

    // =========================================================================
    // GET /messages/recent
    // =========================================================================

    @Test
    void getRecent_returnsAtMostTenMessages() {
        given()
                .when().get("/messages/recent")
                .then()
                .statusCode(200)
                .body("$", hasSize(lessThanOrEqualTo(10)));
    }

    // =========================================================================
    // POST /messages/publish — PublisherManager mockato
    // =========================================================================

    @Test
    void publishMessage_callsPublisherAndReturnsMessageId() {
        Mockito.when(publisherManager.publish(any(EventType.class), anyString(), any()))
                .thenReturn("mock-msg-id-123");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "evento utente", "eventType": "USER_EVENT"}
                        """)
                .when().post("/messages/publish")
                .then()
                .statusCode(200)
                .body("messageId", is("mock-msg-id-123"))
                .body("status", is("published"));

        Mockito.verify(publisherManager)
                .publish(EventType.USER_EVENT, "evento utente", java.util.Map.of("source", "API"));
    }

    @Test
    void publishMessage_publisherThrows_returns500() {
        Mockito.when(publisherManager.publish(any(), anyString(), any()))
                .thenThrow(new RuntimeException("Pub/Sub non disponibile"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "fallisce"}
                        """)
                .when().post("/messages/publish")
                .then()
                .statusCode(500);
    }

    // =========================================================================
    // DELETE /messages/{id}
    // =========================================================================

    @Test
    void deleteMessage_existingId_returns204() {
        Long id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "da cancellare"}
                        """)
                .post("/messages")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");

        given()
                .when().delete("/messages/" + id)
                .then()
                .statusCode(204);

        // Verifica che non esista più
        given()
                .when().get("/messages/" + id)
                .then()
                .statusCode(404);
    }

    @Test
    void deleteMessage_nonExistingId_returns404() {
        given()
                .when().delete("/messages/999999999")
                .then()
                .statusCode(404);
    }

    // =========================================================================
    // GET /messages/dlq
    // =========================================================================

    @Test
    void getDlqMessages_returns200WithList() {
        given()
                .when().get("/messages/dlq")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(0)));
    }

    @Test
    void getDlqMessages_onlyContainsDeadLetteredStatus() {
        // Crea un messaggio normale (ACTIVE) — non deve apparire in /dlq
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"text": "messaggio normale", "eventType": "DEMO"}
                        """)
                .post("/messages")
                .then().statusCode(201);

        // I messaggi in /dlq devono avere tutti status DEAD_LETTERED
        given()
                .when().get("/messages/dlq")
                .then()
                .statusCode(200)
                .body("status", everyItem(is("DEAD_LETTERED")));
    }
}
