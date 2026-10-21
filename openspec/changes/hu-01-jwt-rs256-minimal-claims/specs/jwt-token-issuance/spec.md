# Spec Delta

## Purpose

Define el contrato observable del token que emite el login para que los consumidores puedan verificar su firma, identidad, cliente y vigencia sin alterar los comportamientos del endpoint no afectados por el Issue.

## ADDED Requirements

### Requirement: Login emite un JWT firmado verificable
Cuando autentica credenciales válidas con el cliente permitido, el login SHALL devolver un JWT cuya firma RS256 pueda verificarse con la clave pública correspondiente. La clave privada de firma SHALL permanecer fuera del repositorio.

#### Scenario: Verificación de firma con la clave pública
- **WHEN** un usuario inicia sesión correctamente con `client` igual a `angular-workshop`
- **THEN** la respuesta es `200 OK` y la firma RS256 del token puede verificarse con la clave pública correspondiente

#### Scenario: Clave privada fuera del repositorio
- **WHEN** se inspecciona el repositorio y la configuración documentada de claves
- **THEN** la clave privada no está almacenada en el repositorio y se identifica la clave pública correspondiente para la Requirements API

### Requirement: JWT contiene los claims mínimos acordados
El JWT emitido SHALL contener `sub`, `client`, `groups`, `iat` y `exp`. `sub` SHALL identificar al usuario autenticado, `client` SHALL ser `angular-workshop`, `groups` SHALL ser un arreglo que contenga el `role` persistido del usuario, y `iat` y `exp` SHALL representar tiempos Unix en segundos con `exp` posterior a `iat`.

#### Scenario: Claims del usuario autenticado
- **WHEN** se emite un token para un usuario válido con rol `STUDENT`
- **THEN** el token contiene `sub` con el username autenticado, `client` igual a `angular-workshop`, `groups` igual a `["STUDENT"]`, y los claims `iat` y `exp` con `exp` posterior a `iat`

### Requirement: Login acepta únicamente el cliente permitido
El endpoint SHALL requerir el campo `client` y SHALL aceptar únicamente el valor de cadena `angular-workshop`; la ausencia, nulidad, tipo no textual, valor vacío o cualquier otro valor SHALL producir `400 Bad Request`.

#### Scenario: Cliente permitido
- **WHEN** se envían credenciales válidas y `client` igual a `angular-workshop`
- **THEN** el login continúa la autenticación e incluye ese valor en el JWT

#### Scenario: Cliente ausente o inválido
- **WHEN** falta `client`, es nulo, no es una cadena, está vacío o contiene un valor distinto de `angular-workshop`
- **THEN** el login responde `400 Bad Request` y no emite un token

### Requirement: Expiración del token coincide con expiresIn
La respuesta exitosa SHALL informar `expiresIn` en segundos, coherente con `exp - now` al emitir el token; la diferencia absoluta SHALL ser como máximo un segundo para admitir precisión de segundos.

#### Scenario: Duración informada corresponde a exp
- **WHEN** se valida una respuesta exitosa y se compara `expiresIn` con `exp - now` en el instante de emisión
- **THEN** ambos valores difieren como máximo en un segundo

### Requirement: Login preserva respuestas documentadas no modificadas
El endpoint SHALL preservar `POST /auth/login` sin autenticación previa, el estado `200` y los campos de usuario documentados para credenciales válidas, `400` para request nulo o username/password ausente, nulo o en blanco, y `401` para credenciales inválidas. El cambio de token y la nueva obligatoriedad de `client` son las excepciones comprometidas por el Issue.

#### Scenario: Login válido conserva datos del usuario
- **WHEN** se envían credenciales válidas y el cliente permitido
- **THEN** la respuesta es `200 OK` e incluye `token`, `expiresIn` y `user` con `username`, `displayName` escapado en HTML y `role`

#### Scenario: Credenciales requeridas vacías o ausentes
- **WHEN** el request es nulo, o `username` o `password` es nulo, vacío o en blanco
- **THEN** el login responde `400 Bad Request`

#### Scenario: Credenciales inválidas
- **WHEN** se envían credenciales no correspondientes a un usuario autenticado y el cliente permitido
- **THEN** el login responde `401 Unauthorized`

### Requirement: La observabilidad no expone valores sensibles
El flujo de login SHALL evitar registrar tokens, claves privadas o públicas y otros valores sensibles de autenticación.

#### Scenario: Login exitoso sin exposición de secretos
- **WHEN** se ejecuta un login exitoso y se inspeccionan los registros producidos por el flujo
- **THEN** los registros no contienen el token ni valores de claves u otros secretos de autenticación

#### Scenario: Login fallido sin exposición de secretos
- **WHEN** se ejecuta un login fallido y se inspeccionan los registros producidos por el flujo
- **THEN** los registros no contienen contraseñas, tokens ni valores de claves u otros secretos de autenticación
