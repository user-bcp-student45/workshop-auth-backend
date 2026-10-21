# Spec Delta

## Purpose

Define qué tokens puede aceptar la Requirements API como consumidor del JWT emitido por el login, asegurando que solo confíe en tokens verificables, vigentes y con los claims mínimos acordados.

## ADDED Requirements

### Requirement: Requirements API acepta solo tokens JWT RS256 válidos y vigentes
La Requirements API SHALL aceptar únicamente JWT cuya firma RS256 se verifique con la clave pública correspondiente y cuyo claim `exp` indique que el token no ha expirado.

#### Scenario: Token firmado válido y vigente
- **WHEN** la Requirements API recibe un JWT firmado con RS256 verificable, no expirado y con los claims requeridos
- **THEN** el token pasa la validación de autenticación

#### Scenario: Token expirado
- **WHEN** la Requirements API recibe un JWT cuyo claim `exp` está vencido
- **THEN** rechaza el token

#### Scenario: Firma no verificable
- **WHEN** la Requirements API recibe un token con firma inválida o no verificable mediante la clave pública correspondiente
- **THEN** rechaza el token

### Requirement: Requirements API exige los claims mínimos
La Requirements API SHALL rechazar cualquier token que no contenga `sub`, `client`, `groups`, `iat` y `exp`.

#### Scenario: Token sin un claim requerido
- **WHEN** la Requirements API recibe un token al que le falta cualquiera de `sub`, `client`, `groups`, `iat` o `exp`
- **THEN** rechaza el token

### Requirement: Requirements API valida client estrictamente
La Requirements API SHALL aceptar únicamente un claim `client` de tipo cadena cuyo valor sea exactamente `angular-workshop`; SHALL rechazarlo si está ausente, vacío, no es una cadena o tiene otro valor.

#### Scenario: Cliente permitido
- **WHEN** la Requirements API recibe un JWT válido cuyo claim `client` es la cadena `angular-workshop`
- **THEN** la validación de `client` tiene éxito

#### Scenario: Cliente ausente, vacío o de tipo incorrecto
- **WHEN** la Requirements API recibe un JWT con `client` ausente, vacío o de tipo distinto de cadena
- **THEN** rechaza el token

#### Scenario: Cliente no permitido
- **WHEN** la Requirements API recibe un JWT válido cuyo claim `client` es una cadena distinta de `angular-workshop`
- **THEN** rechaza el token
