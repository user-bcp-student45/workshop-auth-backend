# Proposal

## Why

El login actualmente devuelve un valor Base64 que no es un JWT firmado, por lo que la Requirements API no puede verificar criptográficamente su autenticidad ni confiar en sus claims. El Issue [#12](https://github.com/user-bcp-student45/workshop-auth-backend/issues/12) compromete un contrato de token verificable y una validación consistente de `client`, necesarios para proteger el acceso y mantener alineados emisor y consumidor.

## What Changes

- Emitir en el login un JWT verificable con firma RS256 y los claims requeridos `sub`, `client`, `groups`, `iat` y `exp`.
- Hacer obligatorio en login el campo `client`, aceptar solo `angular-workshop`, incluirlo en el token y exigirlo también en la Requirements API. La ausencia o invalidez del campo se tratará como solicitud inválida.
- Alinear `expiresIn` de la respuesta con `exp - now` dentro de una tolerancia documentada.
- Evitar que logs expongan tokens, claves u otros valores sensibles.
- Preservar los resultados y datos del endpoint de login establecidos por el contrato baseline, salvo los cambios explícitos al token, a la aceptación de `client` y al cálculo de `expiresIn`.

## Capabilities

### New Capabilities

- `jwt-token-issuance`: contrato del JWT que emite `POST /auth/login`, sus claims obligatorios, firma y relación con `expiresIn`.
- `requirements-api-jwt-validation`: contrato de aceptación del token por la Requirements API, incluidos los claims requeridos y la validación estricta de `client`.

### Modified Capabilities

Ninguna. El repositorio no contiene specs existentes; estas capacidades se introducen como contratos nuevos.

## Impact

- API `POST /auth/login`: el token deja de ser el valor Base64 legacy y pasa a ser un JWT RS256; el campo de entrada `client`, opcional e ignorado en el contrato del Día 1, pasa a ser obligatorio y solo se acepta `angular-workshop` (400 si falta o no es válido).
- Respuesta exitosa: se conservan `200`, `token`, `user` y los datos de usuario documentados; `expiresIn` debe corresponder a la expiración efectiva. El campo `debug` legacy no se cambia mediante este Issue.
- Errores del login: se preservan `400` para request/credenciales requeridas vacías o ausentes y `401` para credenciales inválidas. Las reglas del Issue para `client` añaden casos de rechazo que aún no están en el contrato del Día 1.
- Requirements API: su aceptación de tokens debe exigir `sub`, `client`, `groups`, `iat` y `exp`, y rechazar `client` ausente, vacío, de tipo distinto de cadena o distinto de `angular-workshop`. La implementación de ese consumidor no forma parte de este repositorio.
- Se mantienen sin cambios `GET /q/health`, la autenticación previa requerida (ninguna) en `/auth/login` y la respuesta con datos de usuario definida en [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).
- El Issue también compromete pruebas de login válido, credenciales inválidas, token expirado, validación de `client` y ausencia de valores sensibles en logs, además de documentación de configuración de claves. Este cambio define sus resultados observables, no su diseño técnico.
