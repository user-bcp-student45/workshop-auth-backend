package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceServerErrorTest {
    // Sustituto de LegacyDatabase en tests: devuelve un usuario inconsistente solo cuando se activa.
    @Alternative
    @Priority(1)
    @ApplicationScoped
    public static class FaultyDatabase extends LegacyDatabase {
        static volatile boolean returnUserWithoutDisplayName;

        @Override
        public UserRecord findByCredentials(String username, String password) {
            if (returnUserWithoutDisplayName) {
                return new UserRecord(1L, username, null, "STUDENT");
            }
            return super.findByCredentials(username, password);
        }
    }

    @AfterEach
    void restoreDatabase() {
        FaultyDatabase.returnUserWithoutDisplayName = false;
    }

    @Test
    void returns500WithMessageOnUnexpectedFailure() {
        FaultyDatabase.returnUserWithoutDisplayName = true;
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login").then()
                .statusCode(500)
                .body("message", notNullValue());
    }
}
