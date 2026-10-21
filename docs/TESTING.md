# Testing del Backend

## Matriz de cobertura de endpoints

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Credenciales validas (student/Workshop123!) con client=angular-workshop | Camino feliz | 200, token JWT RS256 no nulo, expiresIn=3600, user.username=student |
| POST /auth/login | client ausente | Error documentado (400) | 400, body.message="Client is required" |
| POST /auth/login | client null o en blanco | Caso borde | 400, body.message="Client is required" |
| POST /auth/login | client distinto de angular-workshop | Error documentado (400) | 400, body.message="Client is not allowed" |
| POST /auth/login | client no textual (numero, booleano, objeto, arreglo) | Caso borde | 400, body.message="Client is not allowed" |
| POST /auth/login | client invalido con password incorrecta | Caso borde | 400 (client se valida antes de buscar credenciales) |
| POST /auth/login | Request null | Error documentado (400) | 400, body.message="Request is null" |
| POST /auth/login | username ausente | Error documentado (400) | 400, body.message="Username is required" |
| POST /auth/login | username en blanco | Caso borde | 400, body.message="Username is required" |
| POST /auth/login | password ausente | Error documentado (400) | 400, body.message="Password is required" |
| POST /auth/login | password en blanco | Caso borde | 400, body.message="Password is required" |
| POST /auth/login | credenciales invalidas | Error documentado (401) | 401, body.message="Invalid user <username> or password" |
| POST /auth/login | excepcion interna controlada | Error documentado (500) | 500 con mensaje generico y sin detalles internos |
| GET /q/health | Estado saludable por defecto | Camino feliz | 200, status="UP", checks presente |
| GET /q/health | Query param inesperado | Caso borde | 200, status="UP" |
| GET /q/health | Readiness forzada a DOWN | Error documentado (503) | 503, status="DOWN", checks presente |

## Codigo de tests

### Archivos

- src/test/java/com/bcp/workshop/auth/AuthResourceTest.java
- src/test/java/com/bcp/workshop/auth/HealthEndpointTest.java
- src/test/java/com/bcp/workshop/auth/TestHealthToggle.java

### Test de login (fragmentos agregados)

```java
@Test
void rejectsMissingClient() {
    assertLoginRejected("{\"username\":\"student\",\"password\":\"Workshop123!\"}", "Client is required");
}

@Test
void returns400WhenUsernameIsBlank() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"   \",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Username is required"));
}

@Test
void returns400WhenPasswordIsBlank() {
    given()
            .contentType("application/json")
            .body("{\"username\":\"student\",\"password\":\" \",\"client\":\"angular-workshop\"}")
            .when().post("/auth/login")
            .then()
            .statusCode(400)
            .body("message", is("Password is required"));
}

@Test
void errorResponseIsGenericAndDoesNotExposeInternalDetails() {
    AuthResource resource = new AuthResource();
    resource.authService = new AuthService() {
        @Override
        public LoginResponse login(String username, String password) {
            throw new RuntimeException("boom");
        }
    };

    LoginRequest request = new LoginRequest();
    request.username = "student";
    request.password = "Workshop123!";

    Response response = resource.login(request);
    assertEquals(500, response.getStatus());

    @SuppressWarnings("unchecked")
    Map<String, Object> body = (Map<String, Object>) response.getEntity();
    assertEquals("Internal server error", body.get("message"));
    assertTrue(!body.containsKey("exception"));
}
```

### Test de health (archivo completo)

```java
@QuarkusTest
class HealthEndpointTest {
    @AfterEach
    void clearHealthOverride() {
        TestHealthToggle.forceDown(false);
    }

    @Test
    void healthEndpointReturnsUpInHappyPath() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", notNullValue());
    }

    @Test
    void healthEndpointIgnoresUnexpectedQueryParamAsEdgeCase() {
        given()
                .queryParam("source", "integration-test")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void healthEndpointReturns503WhenAReadinessCheckIsDown() {
        TestHealthToggle.forceDown(true);

        given()
                .when().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks", notNullValue());
    }
}
```

```java
@Readiness
@ApplicationScoped
public class TestHealthToggle implements HealthCheck {
    private static final AtomicBoolean FORCE_DOWN = new AtomicBoolean(false);

    static void forceDown(boolean value) {
        FORCE_DOWN.set(value);
    }

    @Override
    public HealthCheckResponse call() {
        if (FORCE_DOWN.get()) {
            return HealthCheckResponse.down("test-readiness-toggle");
        }
        return HealthCheckResponse.up("test-readiness-toggle");
    }
}
```

## Ejecucion real

### Comando de validacion principal

- Directorio: workshop-auth-backend
- Comando: mvn -B test "-Djacoco.skip=true"
- Codigo de salida: 0

### Resultados de tests (surefire)

- Tests run: 20
- Failures: 0
- Errors: 0
- Skipped: 0

### Cobertura (JaCoCo)

- No medida en esta ejecucion: JaCoCo 0.8.12 es incompatible con JDK 25 local, por lo que se uso -Djacoco.skip=true. Las cifras de ejecuciones anteriores no aplican al estado actual.

## Fallos

- Ningun test en rojo en la ejecucion de mvn test. Durante el cierre aparecen avisos IllegalAccessError de vert.x (thread-local reset en Java 24+); no provocan fallos.

## Pendientes

- Ninguno bloqueante para los endpoints documentados.

## Pruebas propuestas

- Agregar test HTTP de login con JSON malformado para cerrar la pregunta abierta de docs/ENDPOINTS.md sobre comportamiento de parser/Content-Type en runtime.
- Agregar aserciones de estructura minima de checks en /q/health para validar nombre/estado del check cuando hay DOWN.

## Documentos consultados

- docs/ENDPOINTS.md
- REGLAS.md
- docs/GOOD_PRACTICES.md
- PLAN.md
- HALLAZGOS.md
- src/main/java/com/bcp/workshop/auth/AuthResource.java
- src/main/java/com/bcp/workshop/auth/AuthService.java
- src/main/resources/application.properties
- pom.xml
