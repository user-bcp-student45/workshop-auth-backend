# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

### Clave de firma JWT

El login firma los tokens con RS256. La clave privada RSA (2048 bits o mas) no se versiona en este repositorio: se indica con la variable de entorno `AUTH_JWT_PRIVATE_KEY_LOCATION`, que debe apuntar a un archivo PEM (PKCS#8) con la clave privada fuera del repositorio. Sin esta variable la aplicacion arranca, pero el login falla al firmar y responde 500.

Generar un par de claves local, fuera del repositorio:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /ruta/segura/auth-jwt-private.pem
openssl rsa -in /ruta/segura/auth-jwt-private.pem -pubout -out /ruta/segura/auth-jwt-public.pem
```

Ejecutar con la clave configurada. En este repositorio `mvn quarkus:dev` necesita `-Dquarkus.enforceBuildGoal=false`, porque el plugin de Quarkus no declara el goal `build` y, sin esa bandera, Maven omite el arranque:

```bash
export AUTH_JWT_PRIVATE_KEY_LOCATION=/ruta/segura/auth-jwt-private.pem
mvn quarkus:dev -Dquarkus.enforceBuildGoal=false
```

La clave publica se obtiene con `openssl rsa -pubout` a partir de la privada, o usando el archivo `auth-jwt-public.pem` generado arriba. Se entrega a los consumidores que verifican la firma (por ejemplo, la Requirements API); no se incluye en el repositorio.

Las pruebas no requieren esta variable: generan un par de claves temporal en `target/test-keys/`.

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.
