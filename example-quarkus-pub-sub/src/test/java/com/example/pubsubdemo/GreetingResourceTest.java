package com.example.pubsubdemo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Test di sanity: verifica che l'applicazione parta e risponda sull'health endpoint.
 * Richiede docker-compose up (Postgres + emulatore Pub/Sub).
 */
@QuarkusTest
class AppHealthTest {

    @Test
    void healthEndpointIsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
