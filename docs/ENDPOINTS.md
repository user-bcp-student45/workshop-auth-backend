# Contrato de endpoints — workshop-auth-backend

Estado del documento: refleja el código **actual**. H-01 (SQL parametrizado) y H-03 (token firmado) están resueltos; los demás hallazgos de `HALLAZGOS.md` siguen pendientes. Las notas de seguridad pueden cambiar al resolver hallazgos, sin cambiar el contrato.

Base URL local: `http://localhost:8080` (`application.properties:1-2`). En perfil `prod` escucha en `0.0.0.0` (`application.properties:3`).

---

## POST /auth/login

### 1. Qué hace

Verifica las credenciales de un usuario del taller y, si son correctas, entrega un token de sesión junto con los datos básicos del usuario para que el cliente (Angular / API Python) lo use.

### 2. Autenticación

No requiere autenticación ni header de autorización: es el endpoint que la emite. Solo exige `Content-Type: application/json` (`AuthResource.java:16`).

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | sí | Nombre de usuario. No puede ser nulo ni vacío/solo espacios (`AuthResource.java:29`). |
| `password` | string | sí | Contraseña. No puede ser nula ni vacía/solo espacios (`AuthResource.java:32`). |
| `client` | string | no | Se acepta (`LoginRequest.java:5`) pero el servidor no lo usa. |

Campos desconocidos se ignoran (`application.properties:8`, `fail-on-unknown-properties=false`).

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Respuesta exitosa — `200 OK`

| campo | tipo | descripción |
|---|---|---|
| `token` | string | Token de sesión (ver notas de seguridad). |
| `expiresIn` | number (entero) | Vigencia declarada en segundos; siempre `3600` (`AuthResource.java:42`). |
| `user.username` | string | Nombre de usuario almacenado. |
| `user.displayName` | string | Nombre para mostrar, con escape HTML aplicado (`AuthResource.java:45`). |
| `user.role` | string | Rol del usuario. Valores sembrados: `STUDENT`, `TRAINER` (`LegacyDatabase.java:23-24`). |
| `debug` | string | Valor fijo `authenticated-with-legacy-flow` (`AuthResource.java:48`). No es parte funcional; los clientes no deben depender de él. |

El orden de las claves del JSON no está garantizado.

```json
{
  "token": "<JWT RS256 firmado con claims sub, groups, iat y exp>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El valor de `token` es un marcador ilustrativo, no un JWT utilizable. La respuesta real contiene un JWT firmado en formato compacto de tres segmentos, emitido por `LegacyTokenUtil.create`.

### 5. Respuestas de error

Todas son JSON con `message` salvo indicación.

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `400` | El cuerpo deserializa a `null` (`AuthResource.java:26-27`). | `{"message":"Request is null"}` |
| `400` | `username` nulo, vacío o solo espacios (`AuthResource.java:29-30`). | `{"message":"Username is required"}` |
| `400` | `password` nula, vacía o solo espacios (`AuthResource.java:32-33`). | `{"message":"Password is required"}` |
| `401` | No existe un usuario con ese par usuario/contraseña (`AuthResource.java:36-37`). El mensaje incluye el usuario enviado. | `{"message":"Invalid user nobody or password"}` |
| `500` | Excepción no controlada en el Resource (`AuthResource.java:54-58`). Incluye `exception` con el nombre de clase interno. | `{"message":"Unknown error","exception":"java.lang.NullPointerException"}` |

Observaciones verificadas en el código:
- Un fallo de acceso a la base de datos **no** produce 500: `LegacyDatabase.findByCredentials` captura la excepción y devuelve `null` (`LegacyDatabase.java:41-43`), que el cliente ve como `401`.
- El texto de `message` en 500 es el de la excepción (`getMessage()`) o `"Unknown error"` si es nulo; el ejemplo es ilustrativo del formato, no un caso reproducible concreto.

### 6. Notas de seguridad

- **El token está firmado con RS256** y contiene `sub` (username), `groups` (rol), `iat` y `exp` (vigencia de 3600 segundos). Los consumidores deben verificar la firma con la clave pública y validar los claims antes de confiar en él (H-03, resuelto).
- **La expiración no se valida en este servicio:** no existe endpoint ni código que verifique el token; `exp` se emite y los clientes deben validarlo.
- La ubicación de la clave privada de firma se obtiene de `AUTH_TOKEN_SIGNING_KEY_LOCATION`; la clave privada debe provisionarse fuera del repositorio. H-04 sigue pendiente por la configuración CORS permisiva.
- Las contraseñas se almacenan y comparan en texto plano (`LegacyDatabase.java:17,23-24`, H-02, pendiente).
- La consulta usa parámetros (`PreparedStatement`, `LegacyDatabase.java:34-37`), corregido en H-01.
- CORS acepta cualquier origen, con métodos `GET,POST,PUT,DELETE,OPTIONS` y headers `accept,authorization,content-type,x-requested-with` (`application.properties:4-7`, H-04, pendiente).
- No hay límite de intentos ni bloqueo de cuenta en el código revisado.
- Laboratorio intencionalmente vulnerable (`README.md`): no exponer a Internet.

---

## GET /q/health

### 1. Qué hace

Indica si el servicio está arriba y listo para recibir tráfico; sirve para monitoreo y para que orquestadores o balanceadores decidan si enviarle solicitudes.

### 2. Autenticación

No requiere autenticación ni headers. No hay configuración de seguridad en `application.properties`.

### 3. Request

Sin cuerpo, sin parámetros de ruta ni de consulta.

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| — | — | — | No aplica. |

```http
GET /q/health HTTP/1.1
Host: localhost:8080
```

### 4. Respuesta exitosa — `200 OK`

El endpoint lo provee la extensión `quarkus-smallrye-health` (`pom.xml`), no código propio; el proyecto no define health checks personalizados (no hay clases `HealthCheck` en `src/main`). El formato siguiente es el estándar de MicroProfile Health y **no se pudo ejecutar para confirmarlo** en esta revisión (ver preguntas abiertas).

| campo | tipo | descripción |
|---|---|---|
| `status` | string | `UP` si todos los checks pasan. |
| `checks` | array | Detalle de checks; sin checks personalizados se espera vacío. |

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `503` | Algún check reporta `DOWN` (comportamiento estándar de SmallRye Health). Con los checks por defecto no se espera. | `{"status":"DOWN","checks":[]}` (no verificado) |

### 6. Notas de seguridad

- Es público y sin autenticación. No devuelve datos de usuarios.
- El CORS abierto (`application.properties:4-7`) también aplica a este endpoint.

---

## Preguntas abiertas

1. Cuerpo exacto de `/q/health` y de sus errores: no se ejecutó la aplicación (el modo dev de Maven no arrancó en este módulo). Confirmar con una llamada real.
2. Respuesta ante JSON malformado o `Content-Type` distinto de `application/json`: la generan el framework (Quarkus REST), no el código del proyecto; estados y cuerpo no verificados.
3. Si el cuerpo deserializa a `null` ocurre realmente con cuerpo vacío o `null` literal (`AuthResource.java:26`); comportamiento de Quarkus REST para cuerpo vacío no verificado.
4. Qué endpoint o servicio validará el token emitido: no existe en este repositorio.
