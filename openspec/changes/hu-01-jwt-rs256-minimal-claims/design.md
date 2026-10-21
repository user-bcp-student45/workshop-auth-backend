# Diseño

## Contexto

Consulta [proposal.md](proposal.md) para conocer la motivación y las dos specs de capacidades para los requisitos observables.

La aplicación actual, basada en Quarkus 3.27.4, implementa `POST /auth/login` en `AuthResource`. Valida username y password, obtiene el username y el role persistidos mediante `LegacyDatabase`, genera un valor Base64 con un secreto embebido mediante `LegacyTokenUtil` y devuelve un `expiresIn` fijo de 3600 segundos. `pom.xml` no incluye dependencias JWT. La prueba de login existente solo cubre una respuesta exitosa con un token no nulo.

El contrato del Día 1 en `docs/ENDPOINTS.md` también documenta los campos del usuario en la respuesta, el campo `debug` legado, respuestas 400 para credenciales ausentes, 401 para credenciales inválidas y acceso sin autenticación a `GET /q/health`. Actualmente, `client` es opcional y se ignora. Este cambio lo hace obligatorio y solo acepta `angular-workshop`.

Este cambio se limita a `workshop-auth-backend`: no modifica, despliega ni prueba la Requirements API ni ningún otro repositorio. La spec de validación de Requirements API se conserva únicamente como contrato de interoperabilidad del token que emite este backend. El trabajo de este repositorio es emitirlo correctamente y documentar cómo obtener y configurar la clave pública correspondiente para un consumidor.

## Objetivos y aspectos fuera de alcance

**Objetivos:**

- Incorporar un emisor de JWT RS256 conforme a estándares, con los claims mínimos y el comportamiento de expiración definidos en las specs.
- Mantener el material privado de firma fuera del control de versiones y documentar cómo obtener y configurar la clave pública correspondiente para los consumidores del token.
- Preservar los comportamientos no relacionados de la respuesta de login y el contrato del endpoint de salud.
- Establecer pruebas de caracterización y regresión antes de cambiar el token y la validación de solicitudes.
- Dejar documentado el contrato de interoperabilidad del token sin realizar trabajo de integración en otros sistemas.

**Fuera de alcance:**

- Implementar, modificar, desplegar o probar la Requirements API o cualquier otro repositorio o sistema consumidor.
- Rediseñar el almacenamiento de credenciales, el acceso a la base de datos, el manejo de errores del login, CORS, los checks de salud ni el resto de la aplicación.
- Agregar refresh tokens, varias identidades de cliente, un endpoint público JWKS o un protocolo de rotación de claves.
- Seguir aceptando el token Base64 legado como alternativa después de habilitar la emisión RS256.

## Contratos protegidos

- `POST /auth/login` seguirá sin requerir autenticación y recibirá JSON.
- Cuando la autenticación sea exitosa, se preservarán el estado HTTP 200, `user.username`, `user.displayName` escapado en HTML, `user.role` y el campo legado `debug` documentados. Solo se reemplazará el formato del token y se hará que `expiresIn` refleje la vigencia real del token emitido.
- Se preservará HTTP 400 para una solicitud nula y para `username` o `password` ausentes, nulos, vacíos o en blanco; se preservará HTTP 401 para credenciales inválidas. La nueva obligatoriedad de `client` añade los casos HTTP 400 por ausencia o valor inválido, según lo definido en las specs.
- Se preservará el comportamiento de `GET /q/health` y su acceso sin requisitos de autenticación.
- La clave privada de firma no se incluirá en el control de versiones, los artefactos de compilación, las respuestas de la API ni los logs. La documentación de este backend indicará cómo obtener y configurar la clave pública correspondiente, sin cambiar la configuración de ningún consumidor.

## Componentes afectados

- `AuthResource`: aplicar la lista permitida de clientes, mantener el comportamiento actual de autenticación y respuesta, y coordinar la creación del token con su expiración.
- `LoginRequest`: representar el campo de entrada `client` y permitir que sus formas ausente, nula o no textual se rechacen como solicitudes inválidas en lugar de aceptarse silenciosamente.
- `LegacyTokenUtil`: reemplazar su responsabilidad de generar tokens legados por la emisión de JWT, o retirarlo en favor de un componente específico de emisión.
- Configuración de ejecución y documentación de este backend: cargar la clave privada desde una ruta externa protegida y describir cómo obtener la clave pública correspondiente.
- `pom.xml`: agregar la capacidad de generación de JWT compatible con Quarkus seleccionada a continuación.
- `AuthResourceTest` y pruebas de apoyo específicas: caracterizar el contrato actual y luego verificar el nuevo comportamiento del emisor, la validación de solicitudes y la protección de logs sensibles.
- Contrato de interoperabilidad con la Requirements API: se documenta en las specs, pero su implementación, despliegue y pruebas no forman parte de este cambio.

## Decisiones

### Usar SmallRye JWT Build de Quarkus para emitir tokens

Agregar la extensión de Quarkus `quarkus-smallrye-jwt-build`, gestionada por el BOM de Quarkus existente, y usar su generador JWT para establecer los claims requeridos y firmar con RS256. Mantener la creación de tokens detrás de un componente pequeño de emisión para que la validación del endpoint y la construcción de la respuesta no gestionen los detalles criptográficos.

Esta opción utiliza una implementación JWT alineada con el framework y evita construir manualmente encabezados JOSE, segmentos Base64URL y firmas. Como alternativa, implementar directamente el formato JWT con criptografía Java de bajo nivel o agregar una biblioteca JWT no relacionada aumenta el riesgo de errores y el costo de mantenimiento sin aportar una capacidad necesaria.

### Cargar la clave de firma desde una configuración de ejecución protegida

Configurar el emisor para leer una clave privada RSA desde una ruta o montaje de secretos proporcionado por el entorno de despliegue (mediante la configuración de ubicación de clave de firma de SmallRye JWT). No incluir una clave alternativa dentro del repositorio. Documentar cómo obtener y configurar la clave pública correspondiente para consumidores como la Requirements API; este cambio no modifica la configuración de esos consumidores ni devuelve la clave pública desde el endpoint de login.

Usar una clave RSA de al menos 2048 bits. Se descarta incluir una clave de desarrollo en el repositorio o compartir una clave entre desarrollo y producción, porque la divulgación del repositorio o de un artefacto de prueba podría otorgar capacidad para firmar tokens. Un endpoint JWKS y la rotación automatizada son alternativas para despliegues de mayor escala, pero no son necesarios para el contrato de una sola clave de este cambio.

### Derivar la vigencia de la respuesta del mismo instante de emisión que `exp`

Determinar una sola vez el instante de emisión de cada token, derivar `iat` y `exp` de ese instante y de la vigencia configurada, y calcular `expiresIn` de la respuesta usando esos mismos valores. Mantener la vigencia nominal actual de 3600 segundos, salvo que la extensión seleccionada requiera configuración; la condición visible externamente es la diferencia máxima de un segundo establecida en la spec, no una duración fija.

Así se evita que relojes o cálculos de duración independientes produzcan metadatos inconsistentes en la respuesta. Las pruebas deben comparar la duración devuelta con `exp` y con el instante de emisión controlado, en lugar de esperar hasta que el token expire.

### Rechazar un valor de cliente inválido antes de buscar credenciales

Validar que `client` no sea nulo y que sea exactamente la cadena `angular-workshop` antes de consultar las credenciales. Devolver 400 si el campo está ausente, es nulo, está en blanco, no es una cadena o tiene otro valor; no emitir un token. Esto hace determinista el nuevo comportamiento de lista permitida y evita trabajo de autenticación innecesario para clientes no admitidos.

Consultar las credenciales antes de validar `client` no ofrece ventajas de compatibilidad y realiza trabajo sensible de autenticación para solicitudes que no pueden recibir un token.

### Preservar la estructura de respuesta existente

Cambiar el valor de `token` y calcular `expiresIn` a partir de sus claims, preservando el objeto de usuario documentado y el campo `debug`. No acoplar los claims del token a los campos de respuesta más allá del username y el role persistidos necesarios para `sub` y `groups`.

Eliminar `debug` o cambiar otros cuerpos de error no relacionados podría ser conveniente, pero ampliaría este cambio más allá del Issue y del contrato baseline protegido.

## Estrategia incremental

1. Agregar pruebas de caracterización para la respuesta actual de login exitoso y sus campos de usuario y `debug`, los estados de error actuales para credenciales, el escape del nombre visible y el comportamiento del endpoint de salud. Registrar el formato del token legado y el comportamiento de `client` opcional solo como observaciones previas al cambio; no preservarlos como requisitos posteriores.
2. Agregar soporte del emisor y configuración de claves externas sin cambiar todavía el comportamiento del endpoint. Verificar la firma de un token de prueba con su clave pública correspondiente antes de cambiar la emisión en producción.
3. Agregar pruebas para el conjunto y los valores exactos de los claims, la verificación RS256, la expiración, la coherencia de `expiresIn`, los valores inválidos de `client` y la ausencia de datos sensibles en logs capturados. Mantener los contratos caracterizados que las specs protegen.
4. Documentar en este backend la configuración de la clave de firma, cómo obtener su clave pública correspondiente y los claims emitidos para que los consumidores puedan integrarse. No modificar ni ejecutar pruebas en consumidores externos.
5. Verificar dentro de `workshop-auth-backend` la emisión RS256, la verificación de firma con la clave pública de prueba y el comportamiento HTTP del login. La coordinación del despliegue y la validación de extremo a extremo con consumidores quedan fuera de este cambio.

## Pruebas de caracterización previas a cambios críticos

Antes de reemplazar la generación de tokens o cambiar la validación de `client`, ampliar las pruebas HTTP de Quarkus existentes para establecer lo siguiente:

- Un login exitoso devuelve 200 con `token`, `expiresIn` entero, los tres campos de usuario documentados, el nombre visible escapado en HTML y `debug`.
- Una solicitud nula, un username ausente, nulo o en blanco, y un password ausente, nulo o en blanco devuelven 400; las credenciales inválidas devuelven 401.
- `GET /q/health` sigue devolviendo la respuesta saludable documentada y permanece accesible sin autenticación.
- El comportamiento actual de éxito con `client` opcional se registra como baseline y luego se reemplaza explícitamente por pruebas que requieren 400 para la ausencia o invalidez de `client`.

Después de establecer la caracterización baseline, agregar pruebas específicas para verificar RS256 con un par de claves de prueba que no sea de producción; el contenido de `sub`, `client`, `groups`, `iat` y `exp`; la expiración y la concordancia con `expiresIn`; cada forma de `client` inválido (incluidos valores JSON que no sean cadenas); y que los logs capturados de éxitos y fallos no contengan password, token ni material de claves. No validar la corrección criptográfica comprobando únicamente que el token devuelto no sea nulo.

Las pruebas de aceptación y rechazo que ejerciten la Requirements API quedan fuera de este cambio. Las pruebas de este repositorio verifican el token emitido y la firma con su clave pública de prueba; no afirman que un consumidor externo acepte o rechace tokens correctamente.

## Riesgos y concesiones

- **[Cambio incompatible del formato del token y obligatoriedad de `client`]** Los consumidores actuales podrían depender del token Base64 u omitir `client` → desplegar primero la confianza RS256 del consumidor, comunicar el cambio de solicitud y coordinar la transición.
- **[Exposición o discrepancia de la clave privada]** Una clave comprometida en el repositorio, permisos débiles o claves distintas entre emisor y consumidor podrían permitir falsificaciones o rechazar todas las solicitudes → usar montajes de secretos, restringir el acceso, verificar el par de claves durante la validación del despliegue y mantener las claves fuera de logs y artefactos.
- **[Consumidor fuera del alcance]** Las pruebas de este backend no demuestran el comportamiento de aceptación de un consumidor → limitar la evidencia de este cambio a la emisión y verificación local del JWT; la integración del consumidor requiere trabajo independiente y no forma parte de este checklist.
- **[Desfase de relojes]** Los relojes del emisor y del consumidor podrían diferir cerca del vencimiento → sincronizar los relojes del despliegue y usar una política de desfase acotada en el consumidor; no debilitar la comprobación especificada para aceptar tokens vencidos.
- **[Operación con una sola clave]** No se incluye un protocolo de rotación → documentar el procedimiento para reemplazar la clave y tratar el soporte de rotación/JWKS como trabajo independiente antes de que la escala operativa requiera claves simultáneas.
- **[Campo `debug` legado en la respuesta]** Preservarlo mantiene un indicador antiguo → conservarlo sin cambios para mantener compatibilidad en este cambio y tratar su eliminación por separado si se desea.

## Reversión

Este cambio modifica únicamente `workshop-auth-backend`. Si falla antes de habilitar la emisión RS256, mantener este backend en la versión anterior y no habilitar su nueva configuración de firma. Si el fallo ocurre después de la transición, revertir este backend solo si los consumidores configurados pueden aceptar el formato al que se vuelve; la coordinación o reversión de dichos consumidores no es una tarea de este cambio.

No agregar una alternativa de aceptación dual del token Base64 legado como solución rápida de reversión: al no estar firmado, eludiría la nueva garantía de autenticidad. Si no existe una reversión compatible, deshabilitar el login o la aceptación de tokens posteriores mientras se restablecen la clave y la configuración correctas, en lugar de aceptar silenciosamente tokens no verificables.
