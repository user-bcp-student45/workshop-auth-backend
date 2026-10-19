# PLAN — workshop-auth-backend

Alcance: solo H-03, marcado Pendiente en `HALLAZGOS.md`. No modificar código hasta escribir y ejecutar las pruebas de caracterización. Preservar el contrato de `POST /auth/login` de `docs/ENDPOINTS.md`: mismos estados, cuerpos y campos de respuesta, incluido `expiresIn=3600`. No cambiar el contrato del endpoint.

**Aprobación humana:** aprobada por el usuario el 2026-10-19.

## H-03 — Token Base64 con secreto embebido

**Fuente:** `HALLAZGOS.md`, H-03 (Vulnerabilidad, severidad alta, estado Pendiente).

### Problema

`LegacyTokenUtil.create` (`src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`) codifica en Base64 `username|role|expiry|SECRET`. Base64 no aporta confidencialidad ni integridad: quien recibe el token puede leerlo y modificar sus valores, y el secreto está expuesto dentro del mismo token. El formato no contiene una firma verificable ni claims JWT estándar. Esto incumple `docs/GOOD_PRACTICES.md` (tokens firmados, con expiración y claims explícitos; no usar Base64 casero con secretos embebidos).

El servicio emite el token, pero en este repositorio no existe un endpoint que lo valide. Los consumidores externos deberán verificar la firma y los claims antes de confiar en él; coordinar la compatibilidad con ellos sin modificar otros componentes.

### Cambio propuesto

- Sustituir la codificación Base64 propia por un JWT firmado con algoritmo asimétrico RS256, generado mediante `quarkus-smallrye-jwt-build` y sus APIs estándar. La dependencia está justificada para evitar implementar manualmente el formato JWT, la serialización de claims y las operaciones criptográficas; su versión debe quedar gestionada por el BOM Quarkus existente.
- Incluir claims explícitos: `sub` con el username, el rol en el claim estándar `groups` (array con el rol), y `iat`/`exp`. La vigencia del token será de 3600 segundos. El `expiresIn` de la respuesta debe continuar siendo `3600` y concordar con `exp`.
- Configurar la ubicación de la clave privada de firma en `application.properties` mediante una variable externa obligatoria, por ejemplo `smallrye.jwt.sign.key.location=${AUTH_TOKEN_SIGNING_KEY_LOCATION}`. No incluir claves ni valores predeterminados de producción en código o configuración versionada. Provisionar la clave privada fuera del repositorio; compartir con los validadores únicamente la clave pública.
- Usar una clave de prueba aislada y no sensible para el perfil de tests, bajo `src/test/resources`; nunca reutilizarla en producción. No agregar configuración CORS ni abordar otros aspectos de H-04.
- Mantener `LegacyTokenUtil` con una sola responsabilidad: emitir el token. Centralizar la duración como una constante con nombre y reutilizarla para la expiración y `expiresIn`, evitando valores mágicos duplicados. No cambiar la validación/login, el mapeo de usuario ni el formato de la respuesta.
- Actualizar la nota de seguridad y el ejemplo de token en `docs/ENDPOINTS.md` para describir el JWT firmado, sin alterar los campos, estados o cuerpos definidos por el contrato. Tras completar la implementación y las pruebas, actualizar el estado de H-03 en `HALLAZGOS.md`.

### Archivos a tocar al implementar

- `pom.xml`: agregar la extensión `quarkus-smallrye-jwt-build`, gestionada por el BOM.
- `src/main/resources/application.properties`: ubicación externa obligatoria de la clave privada; sin secretos.
- `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`: emisión de JWT firmado, claims y duración centralizada.
- `src/main/java/com/bcp/workshop/auth/AuthResource.java`: reutilizar la duración común para `expiresIn`, conservando exactamente el cuerpo actual.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: caracterización del contrato y pruebas de firma/claims.
- `src/test/resources/application.properties` y clave(s) de prueba: configuración y material criptográfico exclusivo del perfil de tests.
- `docs/ENDPOINTS.md`: actualizar solo las notas de seguridad y el ejemplo del token; no cambiar el contrato.
- `HALLAZGOS.md`: marcar H-03 como resuelto solo después de validar la implementación.

### Pruebas de caracterización y validación

Escribir y ejecutar primero, antes de modificar código de producción, las pruebas de contrato vía `POST /auth/login`. Las pruebas de login existentes cubren parcialmente estos casos; conservarlas y completar las aserciones necesarias:

1. Credenciales válidas de `student` y `trainer` → `200`; comprobar `token` no vacío, `expiresIn=3600` y los valores existentes de `user.username`, `user.displayName` y `user.role`.
2. Contraseña incorrecta y usuario inexistente → `401`, manteniendo el cuerpo y mensaje documentados.
3. Request nulo o credenciales vacías/ausentes → `400`, manteniendo los mensajes documentados.
4. Confirmar que el cuerpo de éxito conserva los mismos campos y que no se cambia ningún estado, mensaje ni campo de respuesta descrito en `docs/ENDPOINTS.md`.

Ejecutar esas pruebas contra el comportamiento actual para establecer la línea base. Antes de implementar la emisión JWT, añadir las pruebas de seguridad para que expresen el resultado esperado (inicialmente fallan por el token legado): tres segmentos JWT; firma RS256 verificable con la clave pública de prueba; claims `sub`, rol, `iat` y `exp`; diferencia `exp - iat` de 3600 segundos; y firma inválida al alterar el contenido del token. Verificar que `expiresIn` sigue concordando con el claim `exp`.

Después de implementar, ejecutar `mvn -B verify`. Criterio de cierre: todas las pruebas pasan, la cobertura del código modificado es al menos 85% (REGLAS.md, regla 7), ningún secreto real está en el repositorio y el consumidor externo puede validar el algoritmo/claims con la clave pública sin acceso a la clave privada. El contrato de `docs/ENDPOINTS.md` permanece intacto.
