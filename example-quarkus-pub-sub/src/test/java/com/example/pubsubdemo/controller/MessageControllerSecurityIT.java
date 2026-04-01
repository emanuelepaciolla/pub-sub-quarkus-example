package com.example.pubsubdemo.controller;

import com.example.pubsubdemo.service.PublisherManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Integration test — sicurezza degli endpoint REST.
 *
 * TDD: questi test sono stati scritti PRIMA dell'aggiunta di @RolesAllowed
 * al controller e della configurazione JWT in application.properties.
 * Verificano la matrice di accesso:
 *
 *  Endpoint                    | Nessun token | READER | PUBLISHER | ADMIN
 *  ----------------------------|--------------|--------|-----------|------
 *  GET  /messages/**           | 401          | 200    | 200       | 200
 *  POST /messages              | 401          | 403    | 201       | 201
 *  POST /messages/publish      | 401          | 403    | 200       | 200
 *  DELETE /messages/{id}       | 401          | 403    | 403       | 204/404
 *  GET  /messages/dlq          | 401          | 403    | 403       | 200
 *  GET  /q/health              | 200          | 200    | 200       | 200
 *
 * Il Pub/Sub NON è richiesto: PublisherManager è mockato.
 */
@QuarkusTest
class MessageControllerSecurityIT {

    @InjectMock
    PublisherManager publisherManager;

    // =========================================================================
    // Nessun token → 401
    // =========================================================================

    @Test
    void noToken_getAllMessages_returns401() {
        given().when().get("/messages").then().statusCode(401);
    }

    @Test
    void noToken_createMessage_returns401() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"text": "non autorizzato"}
                        """)
                .when().post("/messages")
                .then().statusCode(401);
    }

    @Test
    void noToken_publish_returns401() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"text": "non autorizzato"}
                        """)
                .when().post("/messages/publish")
                .then().statusCode(401);
    }

    @Test
    void noToken_delete_returns401() {
        given().when().delete("/messages/1").then().statusCode(401);
    }

    @Test
    void noToken_dlq_returns401() {
        given().when().get("/messages/dlq").then().statusCode(401);
    }

    // =========================================================================
    // READER — può solo leggere
    // =========================================================================

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_getAllMessages_returns200() {
        given().when().get("/messages").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_getRecent_returns200() {
        given().when().get("/messages/recent").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_getById_unknownId_returns404NotForbidden() {
        given().when().get("/messages/999999999").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_createMessage_returns403() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"text": "lettore non può creare"}
                        """)
                .when().post("/messages")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_publish_returns403() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"text": "lettore non può pubblicare"}
                        """)
                .when().post("/messages/publish")
                .then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_delete_returns403() {
        given().when().delete("/messages/1").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "reader", roles = {"READER"})
    void reader_dlq_returns403() {
        given().when().get("/messages/dlq").then().statusCode(403);
    }

    // =========================================================================
    // PUBLISHER — può leggere e pubblicare, non eliminare né vedere DLQ
    // =========================================================================

    @Test
    @TestSecurity(user = "publisher", roles = {"PUBLISHER"})
    void publisher_getAllMessages_returns200() {
        given().when().get("/messages").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "publisher", roles = {"PUBLISHER"})
    void publisher_createMessage_returns201() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"text": "messaggio dal publisher"}
                        """)
                .when().post("/messages")
                .then().statusCode(201);
    }

    @Test
    @TestSecurity(user = "publisher", roles = {"PUBLISHER"})
    void publisher_delete_returns403() {
        given().when().delete("/messages/1").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "publisher", roles = {"PUBLISHER"})
    void publisher_dlq_returns403() {
        given().when().get("/messages/dlq").then().statusCode(403);
    }

    // =========================================================================
    // ADMIN — accesso completo
    // =========================================================================

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void admin_getAllMessages_returns200() {
        given().when().get("/messages").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void admin_dlq_returns200() {
        given().when().get("/messages/dlq").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void admin_delete_nonExisting_returns404NotForbidden() {
        // Verifica che ADMIN ottenga 404 (not found) e non 403 (forbidden)
        given().when().delete("/messages/999999999").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "admin", roles = {"ADMIN"})
    void admin_createMessage_returns201() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"text": "messaggio da admin"}
                        """)
                .when().post("/messages")
                .then().statusCode(201);
    }

    // =========================================================================
    // Endpoint pubblici — accessibili senza token
    // =========================================================================

    @Test
    void health_noAuth_returns200() {
        given().when().get("/q/health").then().statusCode(200);
    }
}
