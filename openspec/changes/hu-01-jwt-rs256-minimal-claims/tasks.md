# Tareas

## 1. Caracterización del contrato actual

- [ ] 1.1 Ampliar las pruebas HTTP de `POST /auth/login` para fijar el baseline antes de cambiar el comportamiento: respuesta exitosa y campos `user`/`debug`, username visible escapado en HTML, errores 400 de solicitud/credenciales requeridas y error 401 por credenciales inválidas; verificar con `mvn test`.
- [ ] 1.2 Caracterizar que hoy el login exitoso acepta `client` ausente y que `GET /q/health` sigue accesible sin autenticación; ejecutar las pruebas de caracterización y registrar estos resultados como comportamiento previo que no debe confundirse con el contrato posterior.

## 2. Validación del cliente

- [ ] 2.1 Agregar pruebas HTTP para `client` ausente, nulo, vacío, no textual o distinto de `angular-workshop`; implementar el rechazo con `400 Bad Request` sin emitir token y verificar que pasan las pruebas de cada caso.
- [ ] 2.2 Verificar mediante pruebas que un `client` igual a `angular-workshop` permite continuar el login y que las credenciales inválidas con ese cliente conservan `401 Unauthorized`.

## 3. Configuración y documentación de claves

- [ ] 3.1 Configurar la carga de la clave privada RSA desde un recurso externo protegido, sin clave alternativa en el repositorio; verificar el arranque y la emisión en pruebas con una clave de prueba no productiva y confirmar que ninguna clave privada queda versionada.
- [ ] 3.2 Documentar en este repositorio la configuración externa de la clave privada y cómo obtener la clave pública correspondiente; actualizar únicamente la documentación afectada de este backend y verificar que describe los nombres de claims, el formato RS256 y el cambio de obligatoriedad de `client` sin incluir material privado ni modificar documentación de consumidores.

## 4. Emisión y respuesta JWT

- [ ] 4.1 Incorporar la capacidad de generación JWT RS256 de Quarkus y emitir un token verificable; comprobar criptográficamente su firma con la clave pública del par de prueba.
- [ ] 4.2 Incluir y probar `sub`, `client`, `groups`, `iat` y `exp` con los valores definidos en la spec, incluido `groups` como arreglo del role persistido; comprobar que `exp` es posterior a `iat`.
- [ ] 4.3 Calcular `expiresIn` a partir del mismo instante de emisión y de `exp`; probar que difiere como máximo un segundo de `exp - now` y que un token recién emitido tiene `exp` posterior al instante actual.
- [ ] 4.4 Confirmar mediante pruebas de regresión que la respuesta exitosa conserva HTTP 200, `user.username`, `user.displayName` escapado, `user.role` y `debug`, reemplazando únicamente el token legacy y su duración fija.

## 5. Protección de información sensible

- [ ] 5.1 Agregar pruebas sobre los registros del flujo de login exitoso y fallido; eliminar cualquier registro de password, token o material de claves y verificar que las pruebas de logs no encuentran valores sensibles.
