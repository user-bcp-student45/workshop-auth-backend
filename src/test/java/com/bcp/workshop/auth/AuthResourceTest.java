package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(JwtTestProfile.class)
class AuthResourceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void returnsDocumentedSuccessfulResponse() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void escapesDisplayNameInSuccessfulHttpResponse() {
        QuarkusMock.installMockForType(new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                return new UserRecord(1L, username, "<student & trainer>", "STUDENT");
            }
        }, LegacyDatabase.class);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.displayName", is("&lt;student &amp; trainer&gt;"));
    }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsNullUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":null,\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsNullPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":null,\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"   \",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrecta\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsMissingClient() {
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\"}", "Client is required");
    }

    @Test
    void rejectsNullClient() {
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":null}", "Client is required");
    }

    @Test
    void rejectsEmptyOrBlankClient() {
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"\"}", "Client is required");
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"   \"}", "Client is required");
    }

    @Test
    void rejectsNonTextClient() {
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":123}", "Client is not allowed");
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":true}", "Client is not allowed");
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":{\"name\":\"angular-workshop\"}}", "Client is not allowed");
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":[\"angular-workshop\"]}", "Client is not allowed");
    }

    @Test
    void rejectsOtherClientValue() {
        assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"otro-cliente\"}", "Client is not allowed");
    }

    @Test
    void validatesClientBeforeCredentialLookup() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrecta\",\"client\":\"otro-cliente\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Client is not allowed"));
    }

    @Test
    void issuesTokenWithMinimalClaimsAndMatchingExpiresIn() throws Exception {
        Response response = given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().response();

        String token = response.path("token");
        long expiresIn = ((Number) response.path("expiresIn")).longValue();
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertEquals("student", claims.get("sub").asText());
        assertEquals("angular-workshop", claims.get("client").asText());
        assertTrue(claims.get("groups").isArray());
        assertEquals(1, claims.get("groups").size());
        assertEquals("STUDENT", claims.get("groups").get(0).asText());
        long iat = claims.get("iat").asLong();
        long exp = claims.get("exp").asLong();
        assertTrue(exp > iat);
        assertTrue(exp > Instant.now().getEpochSecond());
        assertEquals(exp - iat, expiresIn);
    }

    @Test
    void loginLogsDoNotExposeSecrets() {
        List<String> captured = new ArrayList<>();
        SimpleFormatter formatter = new SimpleFormatter();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                captured.add(formatter.formatMessage(record));
                if (record.getThrown() != null) {
                    captured.add(record.getThrown().toString());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger root = Logger.getLogger("");
        root.addHandler(capture);
        String token;
        try {
            token = given()
                    .contentType("application/json")
                    .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(200)
                    .extract().path("token");
            given()
                    .contentType("application/json")
                    .body("{\"username\":\"student\",\"password\":\"incorrecta\",\"client\":\"angular-workshop\"}")
                    .when().post("/auth/login")
                    .then()
                    .statusCode(401);
        } finally {
            root.removeHandler(capture);
        }

        String logs = String.join("\n", captured);
        assertFalse(logs.contains(token));
        assertFalse(logs.contains("Workshop123!"));
        assertFalse(logs.contains("incorrecta"));
        assertFalse(logs.contains("PRIVATE KEY"));
    }

    private static void assertLoginRejected(String body, String message) {
        given()
                .contentType("application/json")
                .body(body)
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is(message))
                .body("token", nullValue());
    }
}
