AUTH_DB_URL='jdbc:sqlite:./data/login-sql-check.db' \
  mvn -Dtest=AuthResourceTest test# HALLAZGOS — workshop-auth-backend

Rutas de evidencia relativas a `src/main/java/com/bcp/workshop/auth/` salvo indicación. Ordenado de mayor a menor severidad. `docs/ENDPOINTS.md` no existía; se generó desde el código.

## Flujo de login mapeado

1. `AuthResource.login` (L24) valida request/usuario/contraseña con `if/else` anidados (L26-L34).
2. Llama `LegacyDatabase.findByCredentials` (L35), que arma el SQL por concatenación y compara contraseña en texto plano (`LegacyDatabase.java:32`).
3. Si hay usuario, `LegacyTokenUtil.create` genera el token firmado (L39) y se arma la respuesta con `expiresIn` de 3600 segundos desde una constante compartida (L42).
4. Cualquier excepción devuelve mensaje y clase interna (L54-L58).
5. Configuración: la ubicación de la clave privada se externaliza mediante `AUTH_TOKEN_SIGNING_KEY_LOCATION`; CORS permanece abierto (`application.properties:4-7`, H-04 pendiente).

## Matriz

| id | hallazgo | tipo | explicacion | evidencia (archivo:linea) | severidad | estado |
|---|---|---|---|---|---|---|
| H-01 | Concatenación de credenciales en SQL | Vulnerabilidad | Usuario y contraseña se insertan sin parametrizar; una entrada manipulada altera la consulta y permite saltar la autenticación o leer datos. | `LegacyDatabase.java:32` | alta | Resuelto. |
| H-02 | Contraseñas en texto plano | Vulnerabilidad | Se almacenan y comparan sin hash; una filtración de la BD expone todas las credenciales reutilizables. | `LegacyDatabase.java:22-24`, `LegacyDatabase.java:32` | alta | Pendiente |
| H-03 | Token Base64 con secreto embebido | Vulnerabilidad | Base64 es reversible y el secreto viaja dentro del propio token; cualquiera puede decodificarlo, forjar tokens (usuario, rol, expiración) y no hay firma ni claims estándar. | `LegacyTokenUtil.java:8`, `LegacyTokenUtil.java:13-15` | alta | Resuelto: JWT RS256 con claims `sub`, `groups`, `iat`, `exp`; clave privada externa. |
| H-04 | Configuración de firma y CORS permisivo | Vulnerabilidad | CORS acepta cualquier origen (`/.*/`), permitiendo que cualquier sitio invoque la API desde el navegador. La ubicación de la clave de firma ya se externaliza por H-03; H-04 sigue pendiente por CORS. | `application.properties:4-7`, `application.properties` (`AUTH_TOKEN_SIGNING_KEY_LOCATION`) | alta | Pendiente |
| H-05 | Respuesta de error expone detalles internos | Vulnerabilidad | El 500 devuelve `getMessage()` y el nombre de la clase de la excepción, revelando internals; además `printStackTrace` en vez de logging controlado y excepciones tragadas en BD (devuelve `null` = 401 ante fallo). | `AuthResource.java:54-58`, `LegacyDatabase.java:26-27`, `LegacyDatabase.java:39-40` | media | Pendiente |
| H-06 | Dependencias vulnerables: Quarkus 3.27.4 y Commons Text 1.9 | Vulnerabilidad | Commons Text 1.9 está afectada por CVE-2022-42889 (Text4Shell, `StringSubstitutor`); en el código solo se usa `StringEscapeUtils.escapeHtml4`, por lo que el riesgo real es bajo pero la dependencia debe actualizarse o eliminarse. Quarkus 3.27.4: advisories por verificar (no confirmado en esta revisión). | `pom.xml:13`, `pom.xml:49`, uso en `AuthResource.java:10`, `AuthResource.java:45` | media | Pendiente |
| H-07 | Resource mezcla validación, negocio, persistencia y respuesta | Refactor | `login` valida, consulta, genera token y arma respuesta con 5 niveles de anidamiento y números mágicos (`3600` duplicado con `LegacyTokenUtil.java:13`); difícil de probar y mantener. | `AuthResource.java:24-53`, `AuthResource.java:42`, `LegacyTokenUtil.java:13` | baja | Pendiente |

## Observaciones adicionales (fuera de RETO.md, sin fila en la matriz)

- `AuthResource.java:37`: el 401 refleja `request.username` en el mensaje.
- `AuthResource.java:48`: campo `debug` en la respuesta de éxito.
- `LegacyDatabase.java:23-24`: credenciales semilla en código (cubierto por H-02).
