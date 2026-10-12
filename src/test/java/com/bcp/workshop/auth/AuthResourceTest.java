package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    private static final String LOGIN_PATH = "/auth/login";

    private static ValidatableResponse login(String username, String password) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"client\":\"angular-workshop\"}";
        return given().contentType("application/json").body(body).when().post(LOGIN_PATH).then();
    }

    @Test
    void logsInWithWorkshopUser() {
        login("student", "Workshop123!")
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithTrainerUser() {
        login("trainer", "Trainer123!")
                .statusCode(200)
                .body("user.role", is("TRAINER"));
    }

    @Test
    void rejectsWrongPassword() {
        login("student", "wrong").statusCode(401);
    }

    @Test
    void rejectsUnknownUser() {
        login("nobody", "Workshop123!").statusCode(401);
    }

    @Test
    void rejectsBlankCredentials() {
        login("", "Workshop123!").statusCode(400);
        login("student", "").statusCode(400);
    }

    @Test
    void rejectsQuoteInCredentialsWithoutServerError() {
        login("stud'ent", "pass'word").statusCode(401);
    }

    @Test
    void returnsFullSuccessContract() {
        login("student", "Workshop123!")
                .statusCode(200)
                .body("user.displayName", is("Workshop Student"))
                .body("user", hasKey("username"))
                .body("user", hasKey("role"))
                .body("token", not(emptyString()));
    }

    @Test
    void acceptsRequestWithoutOptionalClientField() {
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH).then()
                .statusCode(200);
    }

    @Test
    void ignoresUnknownFields() {
        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"extra\":\"x\"}")
                .when().post(LOGIN_PATH).then()
                .statusCode(200);
    }

    @Test
    void rejectsWhitespaceOnlyUsername() {
        login("   ", "Workshop123!").statusCode(400).body("message", is("Username is required"));
    }

    @Test
    void rejectsWhitespaceOnlyPassword() {
        login("student", "   ").statusCode(400).body("message", is("Password is required"));
    }

    @Test
    void rejectsMissingUsernameField() {
        given().contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH).then()
                .statusCode(400).body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPasswordField() {
        given().contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post(LOGIN_PATH).then()
                .statusCode(400).body("message", is("Password is required"));
    }

    @Test
    void rejectsNullBody() {
        given().contentType("application/json").body("null")
                .when().post(LOGIN_PATH).then()
                .statusCode(400).body("message", is("Request is null"));
    }

    @Test
    void unauthorizedResponseCarriesMessage() {
        login("nobody", "wrong").statusCode(401).body("message", containsString("Invalid user"));
    }

    @Test
    void passwordIsCaseSensitive() {
        login("student", "workshop123!").statusCode(401);
    }
}
