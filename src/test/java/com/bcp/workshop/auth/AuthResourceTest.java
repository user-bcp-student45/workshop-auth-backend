package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("token", notNullValue())
                .body("token", not(emptyString()))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void logsInWithTrainerUser() {
        login("trainer", "Trainer123!")
                .statusCode(200)
                .body("user.username", is("trainer"))
                .body("user.displayName", is("Workshop Trainer"))
                .body("user.role", is("TRAINER"))
                .body("expiresIn", is(3600))
                .body("token", not(emptyString()));
    }

    @Test
    void rejectsWrongPassword() {
        login("student", "wrong")
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void rejectsUnknownUser() {
        login("nobody", "Workshop123!")
                .statusCode(401)
                .body("message", is("Invalid user nobody or password"));
    }

    @Test
    void rejectsBlankCredentials() {
        login("", "Workshop123!").statusCode(400).body("message", is("Username is required"));
        login("student", "").statusCode(400).body("message", is("Password is required"));
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
    void rejectsMissingBody() {
        given().contentType("application/json")
                .when().post(LOGIN_PATH).then()
                .statusCode(400).body("message", is("Request is null"));
    }

    @Test
    void rejectsNullUsernameAndPassword() {
        given().contentType("application/json")
                .body("{\"username\":null,\"password\":\"Workshop123!\"}")
                .when().post(LOGIN_PATH).then()
                .statusCode(400).body("message", is("Username is required"));

        given().contentType("application/json")
                .body("{\"username\":\"student\",\"password\":null}")
                .when().post(LOGIN_PATH).then()
                .statusCode(400).body("message", is("Password is required"));
    }

    @Test
    void rejectsMalformedJsonWithEmptyBadRequestBody() {
        Response response = given().contentType("application/json")
                .body("{")
                .when().post(LOGIN_PATH)
                .then().extract().response();

        assertEquals(400, response.statusCode());
        assertEquals("", response.getBody().asString());
    }

    @Test
    void rejectsUnsupportedContentTypeWithEmptyResponseBody() {
        Response response = given().contentType("text/plain")
                .body("not-json")
                .when().post(LOGIN_PATH)
                .then().extract().response();

        assertEquals(415, response.statusCode());
        assertEquals("", response.getBody().asString());
    }

    @Test
    void unauthorizedResponseCarriesMessage() {
        login("nobody", "wrong").statusCode(401).body("message", containsString("Invalid user"));
    }

    @Test
    void passwordIsCaseSensitive() {
        login("student", "workshop123!").statusCode(401);
    }

    @Test
    void issuesRs256JwtWithExpectedClaimsAndLifetime() throws GeneralSecurityException, IOException {
        ValidatableResponse response = login("student", "Workshop123!").statusCode(200);
        String token = response.extract().path("token");
        String[] segments = token.split("\\.", -1);

        assertEquals(3, segments.length);
        assertTrue(hasValidSignature(token));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(segments[0]));
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
        assertEquals("RS256", header.path("alg").asText());
        assertEquals("student", claims.path("sub").asText());
        assertEquals("STUDENT", claims.path("groups").get(0).asText());

        long issuedAt = claims.path("iat").asLong();
        long expiresAt = claims.path("exp").asLong();
        assertEquals(3600, expiresAt - issuedAt);
        assertEquals(3600, response.extract().jsonPath().getInt("expiresIn"));
    }

    @Test
    void rejectsJwtWithModifiedClaims() throws GeneralSecurityException, IOException {
        String token = login("student", "Workshop123!")
                .statusCode(200)
                .extract()
                .path("token");
        String[] segments = token.split("\\.", -1);
        assertEquals(3, segments.length);

        String modifiedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8));

        assertFalse(hasValidSignature(segments[0] + "." + modifiedPayload + "." + segments[2]));
    }

    private static boolean hasValidSignature(String token) throws GeneralSecurityException, IOException {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            return false;
        }

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(testPublicKey());
        verifier.update((segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(segments[2]));
    }

    private static PublicKey testPublicKey() throws GeneralSecurityException, IOException {
        try (InputStream keyResource = AuthResourceTest.class.getResourceAsStream("/jwt-test-public-key.pem")) {
            if (keyResource == null) {
                throw new IOException("Test JWT public key is missing");
            }
            String pem = new String(keyResource.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encodedKey = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
        }
    }
}
