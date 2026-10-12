# PLAN — workshop-auth-backend

Alcance: solo H-01. Contrato de `docs/ENDPOINTS.md` intacto (mismos status y cuerpos de `POST /auth/login`). Sin código modificado todavía.

## H-01 — Concatenación de credenciales en SQL

**Fuente:** `HALLAZGOS.md`, H-01 (Vulnerabilidad, severidad alta, estado Pendiente).

### Problema

`LegacyDatabase.findByCredentials` (`src/main/java/com/bcp/workshop/auth/LegacyDatabase.java:32`) concatena `username` y `password` en el SQL y lo ejecuta con `Statement`. Una entrada con comillas altera la consulta y permite saltar la autenticación. Viola la regla de GOOD_PRACTICES: "SQL: siempre `PreparedStatement` o parámetros nombrados".

### Cambio propuesto

- Reemplazar `Statement` + concatenación por `PreparedStatement` con parámetros `?` para `username` y `password`.
- La consulta pasa a una constante con nombre (`FIND_BY_CREDENTIALS_SQL`), sin valores incrustados.
- Mantener la comparación de contraseña en SQL tal como está hoy; el hash es H-02 y queda fuera de este cambio.
- Clean code (REGLAS.md 8-12):
  - `findByCredentials` hace una sola cosa: ejecutar la consulta y delegar el mapeo a un método privado `toUserRecord(ResultSet)`.
  - Guard clause: `if (!result.next()) return null;` en vez de anidar el mapeo.
  - Sin duplicación ni números mágicos: índices de parámetros expresados por orden en una constante de SQL, nombres de columna reutilizados desde constantes si se repiten.
- Sin dependencias nuevas (`PreparedStatement` es JDBC estándar).
- Fuera de alcance (otros hallazgos): `printStackTrace` (H-05), separación Service/Repository y DTOs (H-07), hash (H-02). No se asume ninguna convención fuera de `docs/GOOD_PRACTICES.md`.

### Archivos a tocar

- `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` (método `findByCredentials`).
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` (nuevas pruebas).
- `HALLAZGOS.md` (actualizar estado de H-01 al terminar).

### Prueba de caracterización (escribir y pasar en verde ANTES de tocar el código)

En `AuthResourceTest`, contra el comportamiento actual, vía `POST /auth/login`:

1. Login válido `student`/`Workshop123!` → 200, `user.username=student`, `user.role=STUDENT`, `token` no nulo, `expiresIn=3600` (ya existe parcialmente: `logsInWithWorkshopUser`).
2. Login válido `trainer`/`Trainer123!` → 200, `user.role=TRAINER`.
3. Contraseña incorrecta → 401.
4. Usuario inexistente → 401.
5. Usuario o contraseña vacíos → 400.
6. Entrada con comillas simples en `username` y `password` (por ejemplo un apóstrofe simple, sin payload de explotación) → 401 con el código corregido. Esta prueba documenta el comportamiento deseado: hoy puede devolver 401 o error según la entrada, por lo que se registra el resultado actual y se confirma que tras el cambio sea 401 estable y sin error 500.

Criterio de cierre: pruebas 1-5 en verde antes y después del cambio sin editarlas; la 6 en verde después. Cobertura del código modificado ≥ 85% (REGLAS.md 7).
