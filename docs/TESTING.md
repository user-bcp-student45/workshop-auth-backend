# Pruebas — workshop-auth-backend

Ejecutar: `mvn test` (JDK 21). Resultado actual: 19 pruebas en verde (`-Djacoco.skip=true` en local por incompatibilidad de JaCoCo 0.8.12 con JDK 25; CI usa JDK 21 y conserva JaCoCo).

## Matriz

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | `student`/`Workshop123!` | camino feliz | 200, `token`, `expiresIn=3600`, `user.username=student`, `user.role=STUDENT` |
| POST /auth/login | `trainer`/`Trainer123!` | camino feliz | 200, `user.role=TRAINER` |
| POST /auth/login | contrato completo de éxito | camino feliz | 200, `user.displayName`, `user.username`, `user.role`, `token` no vacío |
| POST /auth/login | sin campo opcional `client` | caso borde | 200 |
| POST /auth/login | campo desconocido en el JSON | caso borde | 200 (se ignora) |
| POST /auth/login | `username` solo espacios | caso borde | 400 `Username is required` |
| POST /auth/login | `password` solo espacios | caso borde | 400 `Password is required` |
| POST /auth/login | campo `username` ausente | caso borde | 400 `Username is required` |
| POST /auth/login | campo `password` ausente | caso borde | 400 `Password is required` |
| POST /auth/login | contraseña con otra capitalización | caso borde | 401 |
| POST /auth/login | comillas simples en credenciales | caso borde | 401, sin error de servidor |
| POST /auth/login | cuerpo `null` | error 400 | 400 `Request is null` |
| POST /auth/login | `username` vacío | error 400 | 400 |
| POST /auth/login | `password` vacía | error 400 | 400 |
| POST /auth/login | contraseña incorrecta | error 401 | 401 |
| POST /auth/login | usuario inexistente | error 401 | 401 |
| POST /auth/login | 401 con mensaje | error 401 | `message` contiene `Invalid user` |
| POST /auth/login | excepción no controlada en el Resource | error 500 | 500 con `message` |
| GET /q/health | servicio arriba | camino feliz | 200, `status=UP` |
| GET /q/health | sin checks personalizados | caso borde | 200, JSON, `checks` vacío |
| GET /q/health | check en `DOWN` (503) | error | No probado: no hay checks propios para forzarlo; es comportamiento del framework |

## Código

- [`AuthResourceTest.java`](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java): login (16 pruebas).
- [`AuthResourceServerErrorTest.java`](../src/test/java/com/bcp/workshop/auth/AuthResourceServerErrorTest.java): 500. Usa una subclase `@Alternative` de `LegacyDatabase` que devuelve un usuario con `displayName` nulo solo mientras una bandera está activa; así no se agrega dependencia (Mockito).
- [`HealthEndpointTest.java`](../src/test/java/com/bcp/workshop/auth/HealthEndpointTest.java): health (2 pruebas).

## Bugs encontrados (no escondidos en las pruebas)

Las pruebas verifican el contrato mínimo; no fijan el comportamiento defectuoso.

1. **500 expone detalles internos** (`AuthResource.java:54-58`, H-05): el cuerpo incluye `exception` con el nombre de clase. La prueba de 500 solo verifica estado y `message`, no `exception`.
2. **401 refleja el usuario recibido** (`AuthResource.java:37`): la prueba solo verifica `Invalid user`, no el texto completo.
3. **Fallo de BD se ve como 401** (`LegacyDatabase.java:41-43`): no hay prueba que lo consagre; está sin cubrir.
4. **Contraseñas en texto plano / token no firmado / secreto hardcodeado** (H-02, H-03, H-04): sin pruebas que los fijen.

## Cobertura

No medida: JaCoCo 0.8.12 no instrumenta JDK 25 y el equipo solo tiene ese JDK. Por lectura del código, quedan sin cubrir los `catch` de `LegacyDatabase` (`:26-27` y `:41-43`) y la rama de excepción de `initialize`. Confirmar el 85% con el reporte de CI (JDK 21).
