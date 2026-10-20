# Contrato de endpoints — workshop-auth-backend

Base URL de desarrollo: `http://127.0.0.1:8080` (`src/main/resources/application.properties`). En perfil `prod`, el servidor escucha en `0.0.0.0`; el consumidor debe usar el host publicado por su despliegue.

---

## POST /auth/login

### 1. Qué hace

Autentica a un usuario del taller y, si las credenciales coinciden con un usuario almacenado, devuelve sus datos básicos y un token de sesión firmado para que los clientes puedan identificarlo.

### 2. Autenticación

No requiere autenticación previa ni header `Authorization`; este endpoint emite el token. Requiere `Content-Type: application/json` (`AuthResource.java:15-17`). Produce `application/json`.

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| `username` | string | sí | Nombre de usuario. Nulo, vacío o compuesto solo por espacios se rechaza. |
| `password` | string | sí | Contraseña. Nula, vacía o compuesta solo por espacios se rechaza. |
| `client` | string | sí | Identificador del cliente; el único valor permitido es `angular-workshop`. |

Los campos desconocidos del objeto JSON se ignoran (`application.properties`, `quarkus.jackson.fail-on-unknown-properties=false`).

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Response exitosa — `200 OK`

| campo | tipo | descripción |
|---|---|---|
| `token` | string | JWT compacto firmado con RS256. Incluye `sub`, `client`, `groups`, `iat` y `exp`; la duración configurada es de 3600 segundos. |
| `expiresIn` | integer | Duración del token en segundos: `3600`. |
| `user` | object | Datos básicos del usuario autenticado. |
| `user.username` | string | Nombre del usuario almacenado. |
| `user.displayName` | string | Nombre para mostrar; se aplica escape HTML. |
| `user.role` | string | Rol almacenado. Los usuarios semilla usan `STUDENT` o `TRAINER`. |
| `debug` | string | Valor actual fijo `authenticated-with-legacy-flow`. |

Ejemplo completo de la forma de respuesta para el usuario semilla `student` (`AuthResource.java:39-49`, `LegacyDatabase.java:26`):

```json
{
  "token": "<JWT RS256 generado por el servidor para esta respuesta>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

El valor de `token` es dinámico y debe tratarse como credencial; no se publica un token utilizable en este ejemplo. El orden de las claves JSON no está garantizado.

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| `400 Bad Request` | El cuerpo es `null` o está vacío. | `{"message":"Request is null"}` |
| `400 Bad Request` | `username` falta, es nulo, vacío o solo contiene espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | `password` falta, es nula, vacía o solo contiene espacios. | `{"message":"Password is required"}` |
| `400 Bad Request` | `client` falta, es nulo, vacío, contiene solo espacios o no es `angular-workshop`. | `{"message":"Client is required"}` o `{"message":"Unsupported client"}` |
| `400 Bad Request` | El JSON está malformado. Quarkus lo rechaza antes de invocar el método; en la verificación local la respuesta no tenía cuerpo. | *(cuerpo vacío)* |
| `401 Unauthorized` | No se encuentra un usuario con el par recibido. Un fallo de consulta a la base de datos también se convierte en `null` y llega a esta respuesta. El mensaje incluye el username recibido. | `{"message":"Invalid user nobody or password"}` |
| `415 Unsupported Media Type` | El request no usa un tipo de contenido aceptado (`application/json`). En la verificación local la respuesta no tenía cuerpo. | *(cuerpo vacío)* |
| `500 Internal Server Error` | Ocurre una excepción no controlada dentro de `AuthResource.login`, por ejemplo al construir una respuesta con datos inconsistentes o al fallar la firma del token. El contenido de `message` y `exception` depende de la excepción. | `{"message":"<mensaje de la excepción o Unknown error>","exception":"<nombre de la clase de excepción>"}` |

Las respuestas `400` por campos inválidos son construidas por `AuthResource.java:26-33`; el `401`, por `AuthResource.java:35-37`; y el `500`, por `AuthResource.java:53-58`. El JSON malformado y `415` son generados por Quarkus antes de la ejecución del recurso; sus cuerpos vacíos se observaron en una ejecución local.

### 6. Notas de seguridad

- El token se firma con RS256 y expone los claims `sub` (username), `client` (`angular-workshop`), `groups` (rol), `iat` y `exp`. La clave privada se localiza mediante `AUTH_TOKEN_SIGNING_KEY_LOCATION` (`application.properties:9`); debe provisionarse fuera del repositorio. Los consumidores deben validar firma, claims y expiración con la clave pública.
- Este backend emite tokens, pero no contiene un endpoint que los verifique ni aplique autorización a rutas.
- Las contraseñas de los usuarios semilla se almacenan y comparan en texto plano (`LegacyDatabase.java:26-27, 35-38`); no usar estas credenciales fuera del laboratorio.
- El mensaje de `401` refleja el username enviado, y el `500` puede devolver el mensaje y la clase de la excepción (`AuthResource.java:37, 53-58`).
- La consulta de credenciales usa `PreparedStatement` con parámetros (`LegacyDatabase.java:18, 35-38`).
- CORS permite cualquier origen (`application.properties:4-7`). El servicio está marcado como laboratorio intencionalmente vulnerable en `README.md`; no exponerlo a Internet.

---

## GET /q/health

### 1. Qué hace

Indica si el servicio está disponible para recibir tráfico, para que un monitor o balanceador pueda decidir si mantenerlo en servicio.

### 2. Autenticación

No requiere autenticación ni header `Authorization`. La ruta la provee `quarkus-smallrye-health` (`pom.xml`, dependencia `quarkus-smallrye-health`).

### 3. Request

| campo | tipo | obligatorio | descripción |
|---|---|---|---|
| — | — | no | No recibe campos, parámetros ni cuerpo JSON. |

```http
GET /q/health HTTP/1.1
Host: 127.0.0.1:8080
```

### 4. Response exitosa — `200 OK`

| campo | tipo | descripción |
|---|---|---|
| `status` | string | Estado global de salud; la respuesta actual es `UP`. |
| `checks` | array | Checks de salud incluidos en la respuesta; actualmente vacío porque el repositorio no define checks propios. |

Respuesta observada localmente y verificada por `HealthEndpointTest.java:15-27`:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

| estado | cuándo ocurre | ejemplo de cuerpo |
|---|---|---|
| — | El código de este repositorio no define respuestas de error para `/q/health`; la prueba y la verificación local obtuvieron `200 OK` con `checks` vacío. No se encontró un check propio que pueda producir un estado `DOWN`. | No aplica: no hay un cuerpo de error definido por el proyecto. |

### 6. Notas de seguridad

- Es una ruta pública y no devuelve datos de usuarios.
- La configuración CORS actual también permite cualquier origen (`application.properties:4-7`).
- La respuesta `UP` solo informa la salud que reporta SmallRye Health; no comprueba la autenticación ni que un token emitido sea válido.

---

## Preguntas abiertas

1. El comportamiento de negociación cuando el cliente envía un header `Accept` incompatible con `application/json` no quedó confirmado; se omite un código/cuerpo hasta verificarlo.
2. No hay checks personalizados de salud en este repositorio, por lo que no se pudo verificar si una condición `DOWN` llegaría a producir `503` ni cuál sería su cuerpo en este servicio.
3. El cuerpo de `500` varía según la excepción; el código determina sus campos, pero no existe una respuesta única que represente todos los fallos.
4. La validación del JWT emitido corresponde a consumidores externos; no hay endpoint de validación en este backend.

## Documentos consultados

- `docs/GOOD_PRACTICES.md`
- `REGLAS.md`
- `docs/TESTING.md`
- `README.md`
- `RETO.md`
- `HALLAZGOS.md`
- `docs/ENDPOINTS.md` (versión previa del contrato)
