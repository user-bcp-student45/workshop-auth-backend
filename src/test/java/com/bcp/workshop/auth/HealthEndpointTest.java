package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class HealthEndpointTest {
    private static final String HEALTH_PATH = "/q/health";

    @Test
    void reportsUp() {
        given().when().get(HEALTH_PATH).then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void returnsJsonWithoutCustomChecks() {
        given().when().get(HEALTH_PATH).then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("checks", is(empty()));
    }
}
