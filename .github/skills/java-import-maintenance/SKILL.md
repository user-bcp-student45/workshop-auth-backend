---
name: java-import-maintenance
description: 'Clean up unused Java imports and add or repair missing imports while preserving project conventions. Use when cleaning Java imports or resolving missing or unused import diagnostics in workshop-auth-backend.'
---

# Java Import Maintenance

## Objetivo

Eliminar declaraciones `import` no utilizadas y agregar las que falten para tipos o miembros realmente referenciados, conservando el comportamiento, el estilo local y el alcance solicitado. Esta skill aplica a código Java del repositorio `workshop-auth-backend`.

## Lineamientos aplicables

- Consultar `REGLAS.md` y `docs/GOOD_PRACTICES.md` como referencias del proyecto antes de editar.
- Las reglas actuales no definen un orden específico de imports ni una política bancaria de agrupación. No presentar una convención inferida como requisito del banco.
- Conservar el orden, agrupación, separación y estilo que ya tenga cada archivo. Aplicar una regla más específica solo si está documentada en el repositorio.
- Si el proyecto está conectado a SonarQube o SonarLint en modo conectado, usar los hallazgos del proyecto y su Quality Profile activo como referencia. Respetar la regla y ubicación señaladas por el análisis; no cambiar el Quality Profile ni inventar reglas.
- No asumir que SonarQube está configurado. Si no hay conexión, análisis reciente o credenciales disponibles, indicarlo y continuar con las validaciones locales descritas en el procedimiento.
- Un import falta solo si el código referencia un tipo o miembro que no está en el mismo paquete, no pertenece a `java.lang` y no está ya disponible mediante otro import válido. Confirmar el paquete y símbolo exactos en el código fuente, las dependencias o el Java LSP; no adivinar ni agregar dependencias.
- Respetar las reglas de cambios revisables, sin duplicación y con validación; no alterar contratos, lógica, dependencias ni quality gates como parte de esta limpieza.

## Procedimiento

1. Determinar los archivos cubiertos por la solicitud. Limitar la revisión a esos archivos; abarcar todo el proyecto solo si se solicita explícitamente.
2. Leer las instrucciones aplicables y revisar el estado de los archivos para preservar cambios previos del usuario.
3. Detectar imports no utilizados e imports faltantes. Si SonarQube/SonarLint está disponible, revisar sus issues, regla y ubicación; confirmar cada hallazgo en el código y revisar también diagnósticos Java, referencias del archivo y errores de compilación. Revisar imports estáticos, anotaciones, tipos genéricos, referencias de método y usos en inicializadores.
4. Eliminar únicamente imports confirmados como no utilizados. Para cada import faltante, confirmar que el símbolo se usa y resolver su nombre completamente calificado antes de agregar el import específico. Si el bloque contiene una declaración de import mal formada (por ejemplo, un nombre de paquete sin la palabra clave `import`), corregirla solo cuando corresponda a un símbolo utilizado y confirmado.
5. No cambiar imports a comodines, reordenarlos ni modificar código fuente fuera del bloque de imports. No agregar dependencias ni inventar un paquete para resolver un símbolo desconocido; reportar esos casos como bloqueados.
6. Revisar el diff: los únicos cambios permitidos son eliminar imports no utilizados, agregar imports confirmados o corregir declaraciones de import mal formadas. Preservar cualquier otro cambio previo del usuario.
7. Si el proyecto tiene análisis SonarQube configurado y disponible, ejecutar el análisis establecido por el repositorio y comprobar el estado de las issues de imports afectadas. No afirmar que SonarQube pasó si no se ejecutó o no se pudo consultar el análisis.
8. Ejecutar `mvn -B verify` en la raíz del repositorio. Si el entorno no permite completar alguna validación, ejecutar una comprobación local más acotada disponible y declarar con precisión qué quedó sin validar.
9. Informar los archivos revisados, imports eliminados/agregados/corregidos y validaciones ejecutadas; distinguir los resultados de SonarQube de las validaciones locales y no afirmar reglas bancarias no documentadas.

## Criterios de salida

- Cada import eliminado fue confirmado como no utilizado y cada import agregado corresponde a un símbolo referenciado y verificado.
- El diff solo contiene eliminaciones, adiciones o correcciones necesarias en declaraciones de imports; no cambia lógica ni dependencias.
- Se conserva el estilo existente y no se debilitan controles de calidad.
- El resultado de la validación y cualquier limitación se reportan explícitamente.