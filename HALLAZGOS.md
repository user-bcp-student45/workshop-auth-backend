# Hallazgos — workshop-auth-backend

## Estado del registro

Los hallazgos H-01 a H-04 fueron confirmados por la persona usuaria el 2026-10-21. Se verifico en los items disponibles de Project ReqFlow que ninguno tuviera ya el mismo valor en `Hallazgo origen`; el issue HU-01 sobre JWT es un alcance distinto. No se crearon issues.

## H-01 — La consulta de credenciales concatena valores de entrada

- **Estado:** CONFIRMADO
- **Evidencia:** `LegacyDatabase.findByCredentials` concatena `username` y `password` dentro de una sentencia SQL ejecutada con `Statement` ([LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L31-L36)). Esto contradice la convención de consultas parametrizadas en `docs/GOOD_PRACTICES.md`.
- **Por qué importa:** Los valores recibidos durante el login se interpretan como parte de la consulta y no exclusivamente como datos; puede alterarse el resultado de autenticación o producirse un error de consulta.
- **Prioridad propuesta:** Alta.
- **Contrato potencialmente afectado:** `POST /auth/login`: resultado `200` para credenciales válidas, `401` para inválidas y `400` para campos requeridos ausentes o en blanco.
- **Cómo validarlo antes de cambiarlo:** Añadir pruebas de integración con valores de usuario y contraseña que incluyan caracteres de puntuación, y verificar que se tratan como valores literales, no autentican a otro usuario y no producen un `500` inesperado. Confirmar también el resultado con las credenciales válidas e inválidas existentes.

## H-02 — Las contraseñas se almacenan en texto claro

- **Estado:** CONFIRMADO
- **Evidencia:** La tabla `legacy_users` define la columna `password` como `TEXT NOT NULL`, y la inicialización inserta las credenciales de los usuarios de laboratorio directamente en esa columna ([LegacyDatabase.java](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L22-L24)).
- **Por qué importa:** Quien pueda leer la base de datos o una copia de respaldo puede recuperar las contraseñas sin un proceso de verificación de credenciales.
- **Prioridad propuesta:** Alta.
- **Contrato potencialmente afectado:** `POST /auth/login` y las credenciales de laboratorio existentes; el almacenamiento es interno y no está expuesto como contrato HTTP.
- **Cómo validarlo antes de cambiarlo:** Inspeccionar una base de datos temporal creada por las pruebas y comprobar qué representación queda persistida; verificar que el flujo de login sigue autenticando a los usuarios de prueba sin devolver ni registrar contraseñas.

## H-03 — La respuesta `500` expone detalles de la excepción

- **Estado:** CONFIRMADO
- **Evidencia:** El `catch` de `AuthResource.login` copia `error.getMessage()` y `error.getClass().getName()` al cuerpo de la respuesta ([AuthResource.java](src/main/java/com/bcp/workshop/auth/AuthResource.java#L63-L67)). `docs/GOOD_PRACTICES.md` indica que el cliente debe recibir mensajes genéricos; en cambio, `docs/ENDPOINTS.md` documenta actualmente los campos `message` y `exception` para `500`.
- **Por qué importa:** Los mensajes y tipos de excepción internos pueden revelar detalles del runtime o de la operación que falló. Cambiar la respuesta publicada también afectaría un contrato documentado y requiere decisión explícita.
- **Prioridad propuesta:** Media.
- **Contrato potencialmente afectado:** Cuerpo de error `500` de `POST /auth/login`, documentado en `docs/ENDPOINTS.md`.
- **Cómo validarlo antes de cambiarlo:** Provocar una excepción controlada en una prueba aislada y comparar el cuerpo HTTP y los registros capturados; confirmar que la prueba no depende de datos reales ni expone secretos.

## H-04 — CORS permite orígenes de forma amplia

- **Estado:** CONFIRMADO
- **Evidencia:** CORS está habilitado y `quarkus.http.cors.origins` está configurado como la expresión `/.*/` en [application.properties](src/main/resources/application.properties#L4-L7).
- **Por qué importa:** La política no limita el acceso de navegador a orígenes de confianza y puede ampliar qué sitios pueden interactuar con las respuestas del servicio.
- **Prioridad propuesta:** Media.
- **Contrato potencialmente afectado:** Acceso desde navegador a `POST /auth/login`; `docs/ENDPOINTS.md` no establece una lista de orígenes permitidos.
- **Cómo validarlo antes de cambiarlo:** Probar solicitudes `OPTIONS` y solicitudes de navegador desde un origen de prueba permitido y otro no permitido; observar `Access-Control-Allow-Origin` y confirmar qué orígenes reciben autorización.

## Trazabilidad y siguiente gate

- Los IDs H-01 a H-04 se asignaron tras comprobar los valores visibles del campo `Hallazgo origen` en los proyectos ReqFlow accesibles. No se encontro un issue existente para estos hallazgos.
- Las specs correspondientes estan en `openspec/changes/auth-backend-confirmed-findings/specs/` y esperan revision humana antes de pasar a Architect.
- La confirmacion de los hallazgos no crea issues ni decide las preguntas contractuales abiertas de las specs.