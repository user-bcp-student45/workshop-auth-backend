# PLAN — workshop-auth-backend

Alcance histórico: H-03, aprobado el 2026-10-19 y actualmente marcado Resuelto en `HALLAZGOS.md`. Se conserva su plan aprobado como antecedente. Alcance agregado: H-02, H-04, H-05, H-06 y H-07, cada uno con propuesta y pruebas independientes. La aprobación anterior aplica solo a H-03; los items agregados no están aprobados para implementación. No modificar código hasta escribir y ejecutar las pruebas de caracterización. Preservar el contrato de `POST /auth/login` de `docs/ENDPOINTS.md`, excepto los cambios de errores que H-05 requiera y que se documenten explícitamente.

**Aprobación humana:** la aprobación registrada el 2026-10-19 corresponde únicamente al alcance histórico H-03.

## H-03 — Token Base64 con secreto embebido (plan histórico)

**Fuente:** `HALLAZGOS.md`, H-03 (Vulnerabilidad, severidad alta, actualmente Resuelto).

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

---

## Item: H-02 — Contraseñas en texto plano

**Fuente:** `HALLAZGOS.md`, H-02 (vulnerabilidad, severidad alta, Pendiente).

### Problema

`LegacyDatabase` persiste y compara la contraseña como texto plano, e inicializa las cuentas de laboratorio con valores legibles en el SQL. Una copia de la base expone directamente las contraseñas.

### Cambio propuesto

- Usar BCrypt con salt mediante una utilidad de seguridad mantenida por Quarkus; agregar `quarkus-elytron-security-common` solo si no está ya disponible, como dependencia gestionada por el BOM. No implementar el algoritmo manualmente.
- Cambiar el esquema y el acceso para almacenar/verificar hashes, no contraseñas. La migración SQLite debe ser idempotente: convertir filas preexistentes del esquema legado una sola vez, comprobar que la migración terminó y retirar el valor en claro sin dejarlo en logs. No volver a hashear una fila ya migrada.
- Sembrar las cuentas de laboratorio con hashes, sin insertar contraseñas literales desde `LegacyDatabase`. Mantener las credenciales de uso local documentadas y el comportamiento HTTP de login.
- No alterar los campos, estados ni mensajes del contrato de `POST /auth/login`.

### Archivos a tocar

- `pom.xml`: dependencia BCrypt de Quarkus, únicamente si hace falta.
- `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: esquema, migración, seed y verificación de credenciales.
- `src/main/java/com/bcp/workshop/auth/PasswordHasher.java`: nueva responsabilidad aislada de hash/verificación.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y nueva prueba de persistencia/migración: contrato y seguridad del almacenamiento.
- `src/test/resources/application.properties`: usar una base SQLite temporal aislada si la prueba de migración la requiere.
- `HALLAZGOS.md`: marcar H-02 Resuelto solo tras demostrar la migración y verificación.

### Prueba de caracterización antes de tocar código

Ejecutar primero las pruebas de login existentes y completar una caracterización HTTP de cuentas `student` y `trainer` válidas (200 y mismos campos/campos JWT), contraseña incorrecta y usuario inexistente (401 con el mensaje actualmente documentado). Registrar una fila de esquema legado en una base temporal y comprobar el estado actual sin afirmar todavía que el texto claro sea aceptable como resultado final. Después de la línea base, agregar pruebas de corrección: hash almacenado distinto de la contraseña, verificación válida/inválida, migración repetida sin doble hash y ausencia de columna/valor claro tras completarla. No incluir contraseñas reales en fixtures o logs.

---

## Item: H-04 — CORS permisivo

**Fuente:** `HALLAZGOS.md`, H-04 (vulnerabilidad, severidad alta, Pendiente).

### Problema

`application.properties` permite cualquier origen (`/.*/`). Un sitio arbitrario puede iniciar solicitudes de navegador al servicio, ampliando innecesariamente quién puede interactuar con la API.

### Cambio propuesto

- Reemplazar el patrón global por una allowlist configurable externamente, por ejemplo `AUTH_CORS_ORIGINS`; si no se configura, no habilitar orígenes cruzados en vez de caer a `*` o `/.*/`.
- No inventar ni codificar dominios del frontend: los orígenes concretos deben ser proporcionados por los despliegues/consumidores autorizados antes de implementar. Métodos y headers CORS deben limitarse a los necesarios para los endpoints documentados.
- Conservar el contrato HTTP de login y health; el cambio afecta únicamente qué origen de navegador recibe permiso CORS.

### Archivos a tocar

- `src/main/resources/application.properties`: externalizar la allowlist y retirar el origen universal.
- `src/test/java/com/bcp/workshop/auth/CorsConfigurationTest.java`: nueva prueba de integración para origen permitido y origen no permitido.
- `HALLAZGOS.md`: cerrar H-04 solo después de verificar que no queda configuración permisiva.
- Configuración documentada de despliegue, si existe dentro de este repositorio, solo después de recibir los orígenes autorizados.

### Prueba de caracterización antes de tocar código

Con RestAssured y `Origin` explícito, registrar que actualmente una solicitud de login desde un origen arbitrario recibe `Access-Control-Allow-Origin` reflejando dicho origen. Tras la línea base, probar que el origen autorizado configurado recibe el header exacto y que un origen no autorizado no recibe autorización CORS. Probar también el preflight necesario para `POST` solo con los métodos/headers que se decidan. No fijar en la prueba un dominio no aprobado.

### Decisión pendiente

Obtener la lista de orígenes autorizados por ambiente antes de implementar; el backend no debe leer ni asumir configuración de otros componentes del workspace.

---

## Item: H-05 — Respuesta de error expone detalles internos

**Fuente:** `HALLAZGOS.md`, H-05 (vulnerabilidad/deuda de manejo de errores, severidad media, Pendiente).

### Problema

`AuthResource.login` puede devolver `error.getMessage()` y el nombre de clase de la excepción. `LegacyDatabase` usa `printStackTrace`, silencia excepciones y las representa como `null`, por lo que una falla de persistencia termina pareciendo un `401` por credenciales inválidas. Esto expone detalles internos y confunde fallas operativas con autenticación fallida.

### Cambio propuesto

- Devolver al cliente un mensaje genérico en errores internos; no incluir mensajes, clases ni stack traces. Registrar la causa únicamente en el servidor mediante el logger de Quarkus, excluyendo contraseñas, tokens y otros secretos.
- Reemplazar `printStackTrace` y el `null` success-shaped de las fallas de base de datos por una excepción de persistencia tipada. Mapearla a un error interno genérico; reservar el `401` para credenciales no coincidentes.
- Actualizar `docs/ENDPOINTS.md` únicamente en las respuestas de error que cambien por este hallazgo: la respuesta `500` dejará de exponer `exception` y el fallo de base de datos dejará de describirse como `401`. Mantener intactas las respuestas felices y validaciones.

### Archivos a tocar

- `src/main/java/com/bcp/workshop/auth/AuthResource.java`: respuesta genérica y logging controlado.
- `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: eliminar `printStackTrace` y propagar errores de persistencia.
- Nueva excepción tipada de persistencia, si se necesita para separar credenciales inválidas de fallas SQL.
- `src/test/java/com/bcp/workshop/auth/AuthResourceServerErrorTest.java` y `AuthResourceTest.java`: distinguir credenciales inválidas de error de infraestructura sin afirmar detalles internos del 500.
- `docs/ENDPOINTS.md`: reflejar únicamente las respuestas de error modificadas.
- `HALLAZGOS.md`: actualizar H-05 tras validar respuestas y logging.

### Prueba de caracterización antes de tocar código

Primero ejecutar la prueba actual de 500 y agregar una caracterización de falla de base de datos en una instancia SQLite aislada: registrar el comportamiento actual (el error SQL se imprime y la ruta lo convierte en 401). Caracterizar también el status/cuerpo actuales del 500 sin incluir valores concretos de secretos. Después, verificar que una falla SQL produce error interno genérico y no 401, que las credenciales incorrectas siguen produciendo el 401 documentado, y que el cuerpo del 500 no contiene mensaje ni clase de excepción. La evidencia de logging debe confirmar que el detalle queda solo en logs del servidor y que no incluye credenciales.

---

## Item: H-06 — Dependencias vulnerables o con evidencia desactualizada

**Fuente:** `HALLAZGOS.md`, H-06 (vulnerabilidad/deuda de dependencias, severidad media, Pendiente).

### Problema

El hallazgo identifica Commons Text 1.9 como vulnerable a CVE-2022-42889 y menciona Quarkus 3.27.4 con advisories no confirmados. La configuración actual de `pom.xml` muestra Commons Text 1.9, pero Quarkus 3.40.1; por tanto, la evidencia de versión de Quarkus en el hallazgo está desactualizada y no se debe declarar una vulnerabilidad de Quarkus sin verificar el árbol resuelto y los advisories aplicables.

### Cambio propuesto

- Capturar el árbol efectivo (`mvn -B dependency:tree`) y los resultados actuales de Dependency Review/CodeQL antes de cambiar versiones; corregir en H-06 la versión de Quarkus citada y separar vulnerabilidades confirmadas de advisories no aplicables/no confirmados.
- Actualizar Commons Text a una versión compatible y corregida según el advisory vigente, o eliminar la dependencia solo si se reemplaza su uso `escapeHtml4` por una alternativa segura ya disponible. No suprimir ni reducir quality gates.
- Actualizar Quarkus únicamente si el análisis del BOM 3.40.1 y sus dependencias resueltas confirma un CVE aplicable; usar una versión corregida compatible con Java 25.
- No actualizar paquetes sin evidencia ni ampliar el cambio a dependencias sin relación con H-06.

### Archivos a tocar

- `pom.xml`: versión/gestión de Commons Text y, solo si la evidencia lo exige, versión del BOM Quarkus.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: preservar mediante test el escape HTML de `displayName`.
- `HALLAZGOS.md`: registrar versiones analizadas, advisories confirmados y remediación.
- `.github/workflows/ci.yml`: solo si una configuración existente necesitara reflejar una versión de build cambiada; no debilitar CodeQL ni Dependency Review.

### Prueba de caracterización antes de tocar código

Ejecutar `mvn -B dependency:tree` y guardar evidencia de versiones resueltas y alcance (directa/transitiva); ejecutar los análisis de seguridad existentes y guardar resultados antes del cambio. Añadir antes de actualizar la dependencia una prueba de login que asegure que caracteres HTML en `displayName` siguen escapándose en la respuesta. Después del cambio, repetir los mismos análisis y `mvn -B verify`; confirmar que no queda la versión vulnerable confirmada y que el comportamiento de escape no cambia.

---

## Item: H-07 — Resource mezcla responsabilidades

**Fuente:** `HALLAZGOS.md`, H-07 (refactor, severidad baja, Pendiente).

### Problema

`AuthResource.login` concentra validación, acceso a persistencia, emisión de token, mapeo de respuesta y manejo de excepciones, con anidamiento profundo. Esto dificulta mantener y probar cada responsabilidad; el problema coincide con las reglas Clean Code de `REGLAS.md` y con las capas descritas en `docs/GOOD_PRACTICES.md`.

### Cambio propuesto

- Mantener `AuthResource` como adaptador HTTP; trasladar la autenticación y construcción del resultado de negocio a un servicio, y el acceso SQLite a un repositorio definido por una interfaz/implementación concreta.
- Usar DTOs de entrada/salida apropiados en el límite HTTP; no devolver `UserRecord` directamente.
- Introducir guard clauses y métodos de una responsabilidad, evitando duplicar validación o manejar excepciones de infraestructura dentro del recurso.
- Mantener sin cambios el contrato de `POST /auth/login` (campos, estados, mensajes y `expiresIn`) y `GET /q/health`. Resolver H-02 y H-05 de forma aislada; no usar H-07 para mezclar sus cambios de seguridad en una misma modificación.

### Archivos a tocar

- `src/main/java/com/bcp/workshop/auth/AuthResource.java`: delegación HTTP.
- Nueva clase `AuthService.java`: flujo de autenticación y orquestación.
- Nueva interfaz/implementación de repositorio SQLite; mover allí persistencia desde `LegacyDatabase.java`.
- DTO de respuesta nuevo, si hace falta para no exponer el modelo de persistencia.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` y nuevas pruebas unitarias de `AuthService`/repositorio.
- `HALLAZGOS.md`: marcar H-07 Resuelto tras demostrar equivalencia de contrato y cobertura.

### Prueba de caracterización antes de tocar código

Ejecutar primero la suite actual y asegurar que cubre el contrato HTTP completo: login exitoso para ambas cuentas (campos, roles y claims del token), errores 400/401/500 documentados y comportamiento de `GET /q/health`. Añadir las aserciones faltantes antes del refactor, sin cambiar sus resultados esperados. Después, verificar las mismas pruebas HTTP contra las nuevas capas y sumar pruebas unitarias de servicio/repositorio; medir JaCoCo para que las líneas modificadas alcancen al menos 85%.
